package com.example.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.core.content.FileProvider
import com.example.data.local.ScanCacheDao
import com.example.data.local.ScanCacheEntry
import com.example.data.local.ZipMetaDao
import com.example.data.local.ZipMetaEntity
import com.example.data.model.SortBy
import com.example.data.model.Volume
import com.example.data.model.VolumeFilter
import com.example.data.model.ZipEntryInfo
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.util.NaturalOrderComparator
import com.example.util.naturalKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
private const val SCAN_BATCH_SIZE = 50

sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Scanning(val volumesDone: Int, val volumesTotal: Int, val found: Int) : ScanProgress
    data class Done(val total: Int, val elapsedMs: Long) : ScanProgress
    data class Failed(val error: Throwable) : ScanProgress
}

data class ExtractProgress(val done: Int, val total: Int, val currentName: String)

sealed class ZipSlideError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(val path: String) : ZipSlideError(path)
    class FileNotFound(val path: String) : ZipSlideError(path)
    class CorruptZip(val path: String, cause: Throwable) : ZipSlideError(path, cause)
    class EncryptedZip(val path: String) : ZipSlideError(path)
    class ReadOnly(val path: String) : ZipSlideError(path)
    class OutOfSpace : ZipSlideError()
    class SafAccessLost(val uri: Uri) : ZipSlideError(uri.toString())
}

fun ZipSlideError.toUserMessage(): String = when (this) {
    is ZipSlideError.PermissionDenied -> "Storage permission is required to read this folder."
    is ZipSlideError.FileNotFound -> "This archive is no longer available."
    is ZipSlideError.CorruptZip -> "This archive is corrupt and could not be opened."
    is ZipSlideError.EncryptedZip -> "This archive is encrypted and cannot be read."
    is ZipSlideError.ReadOnly -> "This folder is read-only."
    is ZipSlideError.OutOfSpace -> "There is not enough free storage for this operation."
    is ZipSlideError.SafAccessLost -> "Access to the selected folder was lost. Choose it again in Settings."
}

/** The only UI-facing error translation. Never expose filesystem or parser exception text. */
fun Throwable.toUserMessage(): String = (this as? ZipSlideError)?.toUserMessage()
    ?: "That operation could not be completed. Try again."

/**
 * Disk and archive access layer. All blocking work is explicitly dispatched to IO; Room flows are
 * the source of truth so the browser can render cached rows before a revalidation completes.
 */
class ZipRepository(
    private val app: Application,
    private val settingsRepository: SettingsRepository,
    private val zipMetaDao: ZipMetaDao,
    private val scanCacheDao: ScanCacheDao,
    private val volumeRepository: VolumeRepository
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(3)
    private val thumbsDir = File(app.filesDir, "thumbs").apply { mkdirs() }
    private val memoryCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    /** Full-size playback frames. Kept separate from the small, long-lived thumbnail cache. */
    private val frameCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Bitmap?>>()
    private val playbackMutex = Mutex()
    private val playbackArchives = mutableMapOf<String, ZipFile>()
    private val scanMutex = Mutex()
    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    private val _isScanning = MutableStateFlow(false)

    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val cachedWithMeta: Flow<List<Pair<ScanCacheEntry, ZipMetaEntity?>>> =
        combine(scanCacheDao.observeAll(), zipMetaDao.observeAll()) { scans, metas ->
            val metaByPath = metas.associateBy { it.path }
            scans.map { it to metaByPath[it.path] }
        }

    /** Cached rows joined with metadata and current mount state, before the cover rule. */
    val allZipsFlow: StateFlow<List<ZipItem>> = combine(
        cachedWithMeta,
        volumeRepository.observeVolumes(),
        settingsRepository.settings
    ) { cached, volumes, settings ->
        val list = cached.map { (scan, meta) -> scan.toZipItem(meta, volumes) }
        list
            .filter { item -> item.matchesFilter(settings.volumeFilter) }
            .sort(settings.sortBy, settings.ascending, settings.naturalSort, stableSeed = 0L)
    }.distinctUntilChanged().stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    /**
     * The browser library. When [AppSettings.onlyNumberedCovers] is on (the default) an archive is
     * only listed when its cover resolved through the strict `1.x` rule, so folders of ordinary
     * zips never pollute a slideshow library.
     */
    val zipsFlow: StateFlow<List<ZipItem>> = combine(
        allZipsFlow,
        settingsRepository.settings
    ) { list, settings ->
        if (settings.onlyNumberedCovers) list.filter { it.hasCover && it.imageCount > 0 } else list
    }.distinctUntilChanged().stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    /** How many archives the strict `1.x` cover rule is currently hiding. */
    val hiddenByCoverRule: StateFlow<Int> = combine(allZipsFlow, zipsFlow) { all, visible ->
        (all.size - visible.size).coerceAtLeast(0)
    }.distinctUntilChanged().stateIn(repositoryScope, SharingStarted.Eagerly, 0)

    init {
        // Room emits immediately; revalidation runs separately and never blocks first composition.
        repositoryScope.launch { rescanNow() }
        repositoryScope.launch {
            volumeRepository.onMountChanged().collect { rescanNow() }
        }
    }

    fun observeCached(): Flow<List<ZipItem>> = zipsFlow

    fun scan(): Flow<ScanProgress> = flow {
        emit(ScanProgress.Scanning(0, volumeRepository.currentVolumes().count { it.isMounted }, 0))
        rescanNow()
        emit(scanProgress.value)
    }.flowOn(Dispatchers.IO)

    fun triggerRescan() { repositoryScope.launch { rescanNow() } }

    /** Serial revalidation; cached facts are reused whenever file size and mtime are unchanged. */
    suspend fun rescanNow() = scanMutex.withLock {
        if (_isScanning.value) return
        _isScanning.value = true
        val startedAt = System.currentTimeMillis()
        try {
            val settings = settingsRepository.settings.first()
            volumeRepository.refreshVolumes()
            val volumes = volumeRepository.currentVolumes()
                .filter { it.isMounted && it.isReadable && it.matchesFilter(settings.volumeFilter) }
            val existing = scanCacheDao.all().associateBy { it.path }
            val liveByMountedVolume = mutableMapOf<String, MutableSet<String>>()

            _scanProgress.value = ScanProgress.Scanning(0, volumes.size, 0)
            var doneVolumes = 0
            var found = 0
            coroutineScope {
                volumes.map { volume ->
                    async(scanDispatcher) {
                        val entries = scanDirectVolume(volume, settings, existing)
                        synchronized(liveByMountedVolume) {
                            liveByMountedVolume.getOrPut(volume.id) { linkedSetOf() }.addAll(entries.map { it.path })
                        }
                        entries
                    }
                }.awaitAll().forEach { entries ->
                    for (batch in entries.chunked(SCAN_BATCH_SIZE)) scanCacheDao.upsertAll(batch)
                    doneVolumes++
                    found += entries.size
                    _scanProgress.value = ScanProgress.Scanning(doneVolumes, volumes.size, found)
                }
            }

            // SAF roots are independent of a removable volume. They remain available as long as
            // their persisted grant is valid and are scanned recursively with the same cache rule.
            settings.customScanRoots.forEach { root ->
                val safEntries = scanSafRoot(Uri.parse(root), settings, existing)
                for (batch in safEntries.chunked(SCAN_BATCH_SIZE)) scanCacheDao.upsertAll(batch)
                val id = safVolumeId(root)
                liveByMountedVolume.getOrPut(id) { linkedSetOf() }.addAll(safEntries.map { it.path })
                found += safEntries.size
            }

            // Preserve all cached rows for unmounted volumes. Only entries on a volume that was
            // actually traversed can be considered missing and pruned.
            val protectedPaths = existing.values
                .filter { cache -> cache.volumeId !in liveByMountedVolume.keys }
                .map { it.path }
            val livePaths = (protectedPaths + liveByMountedVolume.values.flatten()).distinct()
            scanCacheDao.pruneMissing(livePaths)
            sweepThumbnailCache(livePaths.toSet())
            _scanProgress.value = ScanProgress.Done(livePaths.size, System.currentTimeMillis() - startedAt)
        } catch (error: Throwable) {
            _scanProgress.value = ScanProgress.Failed(error)
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun scanDirectVolume(
        volume: Volume,
        settings: AppSettings,
        existing: Map<String, ScanCacheEntry>
    ): List<ScanCacheEntry> = withContext(scanDispatcher) {
        val root = volume.root ?: return@withContext emptyList()
        val result = mutableListOf<ScanCacheEntry>()
        fun walk(directory: File, depth: Int) {
            coroutineContext.ensureActive()
            if (depth > 8 || !directory.canRead()) return
            directory.listFiles()?.forEach { file ->
                val name = file.name
                if (file.isDirectory) {
                    if (name.equals("Android", true) || name.equals(".thumbnails", true) || (!settings.showHidden && name.startsWith('.'))) return@forEach
                    if (settings.includeSubfolders) walk(file, depth + 1)
                } else if (name.endsWith(".zip", true) && (settings.showHidden || !name.startsWith('.'))) {
                    val path = file.canonicalOrAbsolute()
                    val unchanged = existing[path]?.takeIf {
                        it.sizeBytes == file.length() && it.lastModified == file.lastModified()
                    }
                    result += unchanged ?: inspectDirect(file, volume.id)
                }
            }
        }
        walk(root, 0)
        result
    }

    private suspend fun scanSafRoot(
        root: Uri,
        settings: AppSettings,
        existing: Map<String, ScanCacheEntry>
    ): List<ScanCacheEntry> = withContext(scanDispatcher) {
        val result = mutableListOf<ScanCacheEntry>()
        val volumeId = safVolumeId(root.toString())
        fun visit(parentDocumentId: String, depth: Int) {
            coroutineContext.ensureActive()
            if (depth > 8) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, parentDocumentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            app.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val displayName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val size = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modified = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(id)
                    val name = cursor.getString(displayName).orEmpty()
                    val isDirectory = cursor.getString(mime) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isDirectory) {
                        if (name.equals("Android", true) || name.equals(".thumbnails", true) || (!settings.showHidden && name.startsWith('.'))) continue
                        if (settings.includeSubfolders) visit(documentId, depth + 1)
                    } else if (name.endsWith(".zip", true) && (settings.showHidden || !name.startsWith('.'))) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(root, documentId)
                        val path = uri.toString()
                        val fileSize = cursor.getLong(size)
                        val lastModified = cursor.getLong(modified)
                        val unchanged = existing[path]?.takeIf { it.sizeBytes == fileSize && it.lastModified == lastModified }
                        result += unchanged ?: inspectSaf(uri, name, fileSize, lastModified, volumeId)
                    }
                }
            }
        }
        runCatching { visit(DocumentsContract.getTreeDocumentId(root), 0) }
        result
    }

    private fun inspectDirect(file: File, volumeId: String): ScanCacheEntry {
        val path = file.canonicalOrAbsolute()
        return try {
            ZipFile(file).use { archive ->
                val images = archive.entries().asSequence()
                    .filter { !it.isDirectory && it.name.isImageEntry() }
                    .map { it.name }
                    .toList()
                val (_, hasCover) = resolveCoverEntry(images)
                ScanCacheEntry(path, volumeId, file.name, file.length(), file.lastModified(), images.size, hasCover, false, false, System.currentTimeMillis())
            }
        } catch (exception: Throwable) {
            val encrypted = exception.message.orEmpty().contains("encrypt", true) || exception.message.orEmpty().contains("password", true)
            ScanCacheEntry(path, volumeId, file.name, file.length(), file.lastModified(), 0, false, encrypted, !encrypted, System.currentTimeMillis())
        }
    }

    private fun inspectSaf(uri: Uri, displayName: String, size: Long, lastModified: Long, volumeId: String): ScanCacheEntry {
        return try {
            val images = app.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    buildList {
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.isImageEntry()) add(entry.name)
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            } ?: emptyList()
            val (_, hasCover) = resolveCoverEntry(images)
            ScanCacheEntry(uri.toString(), volumeId, displayName, size, lastModified, images.size, hasCover, false, false, System.currentTimeMillis())
        } catch (exception: Throwable) {
            ScanCacheEntry(uri.toString(), volumeId, displayName, size, lastModified, 0, false, false, true, System.currentTimeMillis())
        }
    }

    /** Exact 1.jpg cover priority. `hasCover` is false only for natural-sort fallback. */
    fun resolveCoverEntry(imageEntries: List<String>): Pair<String?, Boolean> {
        val candidates = imageEntries.filter { it.isImageEntry() }
        fun pick(exactName: String) = candidates
            .filter { it.substringAfterLast('/').equals(exactName, ignoreCase = true) }
            .minWithOrNull(Comparator { first, second ->
                val depth = first.count { it == '/' }.compareTo(second.count { it == '/' })
                if (depth != 0) depth else NaturalOrderComparator.compare(first, second)
            })
        pick("1.jpg")?.let { return it to true }
        pick("1.jpeg")?.let { return it to true }
        pick("1.png")?.let { return it to true }
        pick("1.webp")?.let { return it to true }
        pick("1.gif")?.let { return it to true }
        candidates.firstOrNull { candidate ->
            val base = candidate.substringAfterLast('/')
            base.startsWith("1.", ignoreCase = true) && base.substringAfterLast('.').lowercase() in IMAGE_EXT
        }?.let { return it to true }
        return candidates.minWithOrNull(NaturalOrderComparator) to false
    }

    /** Three-tier cache with one producer per key. Disk writes are atomic. */
    suspend fun thumbnail(zip: ZipItem, px: Int): Bitmap? {
        val key = thumbnailKey(zip.path, zip.lastModified, zip.size, px)
        memoryCache.get(key)?.let { return it }
        val disk = thumbnailFile(key)
        if (disk.exists()) decodeDiskThumbnail(key, disk)?.let { return it }
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: repositoryScope.async(thumbnailDispatcher) {
                generateThumbnail(zip, px, key, disk)
            }.also { inFlight[key] = it }
        }
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) inFlightMutex.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
        }
    }

    private fun decodeDiskThumbnail(key: String, file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath)?.also { memoryCache.put(key, it) }
    }.getOrNull()

    private suspend fun generateThumbnail(zip: ZipItem, px: Int, key: String, disk: File): Bitmap? = withContext(thumbnailDispatcher) {
        coroutineContext.ensureActive()
        val bytes = openEntryBytes(zip, zip.coverEntryName ?: findCover(zip) ?: return@withContext null) ?: return@withContext null
        coroutineContext.ensureActive()
        val bitmap = decodeSampled(bytes, px) ?: return@withContext null
        val tmp = File(disk.parentFile, "${disk.name}.tmp")
        try {
            FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            coroutineContext.ensureActive()
            if (!tmp.renameTo(disk)) {
                disk.delete()
                tmp.copyTo(disk, overwrite = true)
                tmp.delete()
            }
            memoryCache.put(key, bitmap)
            bitmap
        } catch (cancelled: Throwable) {
            tmp.delete()
            throw cancelled
        }
    }

    private suspend fun findCover(zip: ZipItem): String? = withContext(Dispatchers.IO) {
        val names = readEntryMetadata(zip).filter { it.isImage }.map { it.entryPath }
        resolveCoverEntry(names).first
    }

    suspend fun listEntries(zip: ZipItem): List<ZipEntryInfo> = withContext(Dispatchers.IO) {
        readEntryMetadata(zip).map { it.toInfo() }
    }

    suspend fun listImageEntries(zip: ZipItem): List<ZipEntryInfo> = withContext(Dispatchers.IO) {
        readEntryMetadata(zip).filter { it.isImage }.sortedBy { naturalKey(it.entryPath) }.map { it.toInfo() }
    }

    // Existing screens consume the richer row type; it is backed by the same natural ordering.
    suspend fun getZipEntries(zip: ZipItem): List<ZipEntryItem> = withContext(Dispatchers.IO) {
        readEntryMetadata(zip).sortedWith(compareBy<ZipEntryItem> { !it.isImage }.thenBy { naturalKey(it.entryPath) })
    }

    private fun readEntryMetadata(zip: ZipItem): List<ZipEntryItem> = try {
        when {
            zip.isSaf -> app.contentResolver.openInputStream(Uri.parse(zip.path))?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { input ->
                    buildList {
                        var entry = input.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && !entry.name.startsWith("__MACOSX/")) add(entry.toItem())
                            input.closeEntry(); entry = input.nextEntry
                        }
                    }
                }
            } ?: emptyList()
            else -> ZipFile(File(zip.path)).use { archive -> archive.entries().asSequence().filter { !it.isDirectory && !it.name.startsWith("__MACOSX/") }.map { it.toItem() }.toList() }
        }
    } catch (_: Throwable) { emptyList() }

    suspend fun readEntry(zip: ZipItem, entryName: String, targetPx: Int, highQuality: Boolean = false): Bitmap? =
        withContext(decodeDispatcher) {
            // Decoding used to inherit the caller's dispatcher, which put multi-megapixel work on
            // the main thread during playback. It is now always off the UI thread.
            openEntryBytes(zip, entryName)?.let { decodeSampled(it, targetPx, highQuality) }
        }

    /** Holds one ZipFile for a direct-file slideshow; SAF archives retain their streaming backend. */
    suspend fun beginPlayback(zip: ZipItem) = withContext(Dispatchers.IO) {
        if (zip.isSaf) return@withContext
        playbackMutex.withLock {
            if (playbackArchives[zip.path] == null) playbackArchives[zip.path] = ZipFile(File(zip.path))
        }
    }

    suspend fun endPlayback(path: String) = withContext(Dispatchers.IO) {
        playbackMutex.withLock { playbackArchives.remove(path)?.close() }
        frameCache.evictAll()
    }

    /**
     * Slideshow frame loader. Decoded frames are memoised so re-visiting a frame (scrubbing back,
     * looping a short archive) is instant and never re-inflates the same JPEG twice.
     */
    suspend fun loadFrameBitmap(
        zipPath: String,
        entryName: String,
        maxDim: Int = 1600,
        highQuality: Boolean = true
    ): Bitmap? {
        val key = "$zipPath|$entryName|$maxDim|$highQuality"
        frameCache.get(key)?.let { return it }
        val zip = findZip(zipPath) ?: ephemeralZip(zipPath)
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: repositoryScope.async(decodeDispatcher) {
                frameCache.get(key) ?: readEntry(zip, entryName, maxDim, highQuality)?.also { frameCache.put(key, it) }
            }.also { inFlight[key] = it }
        }
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) inFlightMutex.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
        }
    }

    /** Drops decoded frames for an archive once its playback session ends. */
    fun releaseFrames() = frameCache.evictAll()

    private suspend fun openEntryBytes(zip: ZipItem, entryName: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (zip.isSaf) {
                app.contentResolver.openInputStream(Uri.parse(zip.path))?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { input ->
                        var entry = input.nextEntry
                        while (entry != null) {
                            if (entry.name == entryName) return@withContext input.readBytes()
                            input.closeEntry(); entry = input.nextEntry
                        }
                    }
                }
                null
            } else {
                val playbackArchive = playbackMutex.withLock { playbackArchives[zip.path] }
                if (playbackArchive != null) {
                    playbackArchive.getEntry(entryName)?.let { entry ->
                        playbackArchive.getInputStream(entry).use(InputStream::readBytes)
                    }
                } else {
                    ZipFile(File(zip.path)).use { archive ->
                        archive.getEntry(entryName)?.let { entry -> archive.getInputStream(entry).use(InputStream::readBytes) }
                    }
                }
            }
        } catch (_: Throwable) { null }
    }

    suspend fun rename(zip: ZipItem, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = newName.trim().let { if (it.endsWith(".zip", true)) it else "$it.zip" }
            require(normalized.isNotBlank() && !normalized.contains('/') && !normalized.contains('\\'))
            if (zip.isSaf) {
                val renamed = DocumentsContract.renameDocument(app.contentResolver, Uri.parse(zip.path), normalized)
                    ?: throw ZipSlideError.ReadOnly(zip.path)
                scanCacheDao.delete(zip.path); zipMetaDao.delete(zip.path); invalidatePath(zip.path); triggerRescan()
                return@withContext Result.success(File(renamed.path ?: normalized))
            }
            val source = File(zip.path)
            if (!source.exists()) throw ZipSlideError.FileNotFound(zip.path)
            if (!source.canWrite() || source.parentFile?.canWrite() == false) throw ZipSlideError.ReadOnly(zip.path)
            val target = File(source.parentFile, normalized)
            if (target.exists()) throw IOException("A file with this name already exists.")
            if (!source.renameTo(target)) throw IOException("Could not rename archive.")
            scanCacheDao.delete(zip.path); zipMetaDao.delete(zip.path); invalidatePath(zip.path); triggerRescan()
            target
        }
    }

    suspend fun renameZip(zip: ZipItem, newName: String): Result<ZipItem> = rename(zip, newName).map { file ->
        zip.copy(path = file.canonicalOrAbsolute(), name = file.name, thumbnailPath = null)
    }

    suspend fun delete(zip: ZipItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deleted = if (zip.isSaf) DocumentsContract.deleteDocument(app.contentResolver, Uri.parse(zip.path)) else {
                val file = File(zip.path)
                if (!file.exists()) true else file.delete()
            }
            if (!deleted) throw ZipSlideError.ReadOnly(zip.path)
            scanCacheDao.delete(zip.path); zipMetaDao.delete(zip.path); invalidatePath(zip.path)
        }
    }

    suspend fun deleteZip(zip: ZipItem): Result<Unit> = delete(zip)

    suspend fun copyTo(zip: ZipItem, destDirUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(destDirUri)
            val destinationRoot = DocumentsContract.buildDocumentUriUsingTree(destDirUri, rootId)
            val created = DocumentsContract.createDocument(app.contentResolver, destinationRoot, "application/zip", zip.name)
                ?: throw ZipSlideError.ReadOnly(destDirUri.toString())
            app.contentResolver.openOutputStream(created)?.use { output ->
                openArchiveStream(zip)?.use { input -> input.copyTo(output) } ?: throw ZipSlideError.FileNotFound(zip.path)
            } ?: throw ZipSlideError.ReadOnly(created.toString())
            created
        }
    }

    suspend fun moveTo(zip: ZipItem, destDirUri: Uri): Result<Uri> = copyTo(zip, destDirUri).onSuccess { delete(zip) }

    fun extractAll(zip: ZipItem, destDirUri: Uri): Flow<ExtractProgress> = flow {
        val images = listImageEntries(zip)
        val total = images.size
        if (total == 0) return@flow
        val rootId = DocumentsContract.getTreeDocumentId(destDirUri)
        val destinationRoot = DocumentsContract.buildDocumentUriUsingTree(destDirUri, rootId)
        images.forEachIndexed { index, entry ->
            val target = DocumentsContract.createDocument(app.contentResolver, destinationRoot, mimeFor(entry.name), entry.name.substringAfterLast('/'))
                ?: throw ZipSlideError.ReadOnly(destDirUri.toString())
            app.contentResolver.openOutputStream(target)?.use { output ->
                openEntryBytes(zip, entry.name)?.inputStream()?.use { it.copyTo(output) } ?: throw ZipSlideError.CorruptZip(zip.path, IOException("Missing entry"))
            } ?: throw ZipSlideError.ReadOnly(target.toString())
            emit(ExtractProgress(index + 1, total, entry.name.substringAfterLast('/')))
        }
    }.flowOn(Dispatchers.IO)

    /** Legacy direct-folder adapter used by the existing action sheet. */
    suspend fun extractImages(zip: ZipItem, destDir: File? = null, onProgress: (Int, Int, String) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = destDir ?: File(File(zip.path).parentFile ?: app.filesDir, "${zip.name.substringBeforeLast('.')}_extracted")
            if (!target.exists() && !target.mkdirs()) throw ZipSlideError.ReadOnly(target.path)
            if (!target.canWrite()) throw ZipSlideError.ReadOnly(target.path)
            val images = listImageEntries(zip)
            images.forEachIndexed { index, entry ->
                val out = File(target, entry.name.substringAfterLast('/'))
                openEntryBytes(zip, entry.name)?.inputStream()?.use { input -> FileOutputStream(out).use(input::copyTo) }
                    ?: throw ZipSlideError.CorruptZip(zip.path, IOException("Missing entry"))
                onProgress(index + 1, images.size, out.name)
            }
            target
        }
    }

    fun shareUri(zip: ZipItem): Uri = if (zip.isSaf) Uri.parse(zip.path) else FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", File(zip.path))

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memoryCache.evictAll(); frameCache.evictAll(); thumbsDir.listFiles()?.forEach(File::delete)
    }

    suspend fun clearThumbnailCache() { clearCache(); rescanNow() }
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) { thumbsDir.listFiles()?.sumOf(File::length) ?: 0L }
    suspend fun regenerateAll() { clearCache(); rescanNow() }

    suspend fun toggleFavorite(path: String) = withContext(Dispatchers.IO) {
        val current = zipMetaDao.get(path) ?: ZipMetaEntity(path = path)
        zipMetaDao.upsert(current.copy(favorite = !current.favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateLastFrame(path: String, frameIndex: Int) = withContext(Dispatchers.IO) {
        val current = zipMetaDao.get(path) ?: ZipMetaEntity(path = path)
        zipMetaDao.upsert(current.copy(lastFrameIndex = frameIndex, updatedAt = System.currentTimeMillis()))
    }

    suspend fun finishWatching(path: String, frameIndex: Int) = withContext(Dispatchers.IO) {
        val current = zipMetaDao.get(path) ?: ZipMetaEntity(path = path)
        zipMetaDao.upsert(current.copy(lastFrameIndex = frameIndex, watchCount = current.watchCount + 1, updatedAt = System.currentTimeMillis()))
    }

    fun updateMountStates() { volumeRepository.refreshVolumes() }

    suspend fun findZipItem(path: String): ZipItem? = zipsFlow.first().firstOrNull { it.path == path }
    private suspend fun findZip(path: String): ZipItem? = findZipItem(path)
    private fun ephemeralZip(path: String): ZipItem {
        val file = File(path)
        return ZipItem(path, file.name.ifBlank { path.substringAfterLast('/') }, file.length(), file.lastModified(), "primary", "Internal storage", isSaf = path.startsWith("content://"))
    }

    private fun ScanCacheEntry.toZipItem(meta: ZipMetaEntity?, volumes: List<Volume>): ZipItem {
        val volume = volumes.firstOrNull { it.id == volumeId }
        val isSaf = path.startsWith("content://")
        val source = if (isSaf) null else File(path)
        val key = thumbnailKey(path, lastModified, sizeBytes, 512)
        return ZipItem(
            path = path,
            name = fileName,
            size = sizeBytes,
            lastModified = lastModified,
            volumeId = volumeId,
            volumeName = volume?.label ?: if (volumeId.startsWith("sdcard:")) "SD card" else if (volumeId.startsWith("usb:")) "USB" else "Internal storage",
            isMounted = if (isSaf) true else volume?.isMounted ?: false,
            isSaf = isSaf,
            imageCount = imageCount,
            hasCover = hasCover,
            thumbnailPath = thumbnailFile(key).takeIf(File::exists)?.absolutePath,
            isEncrypted = isEncrypted,
            isCorrupt = isCorrupt,
            isZeroImages = imageCount == 0,
            isFavorite = meta?.favorite ?: false,
            lastFrameIndex = meta?.lastFrameIndex ?: 0,
            isReadOnly = !isSaf && (source?.canWrite() == false || source?.parentFile?.canWrite() == false),
            parentFolder = source?.parent.orEmpty(),
            watchCount = meta?.watchCount ?: 0
        )
    }

    private fun List<ZipItem>.sort(sortBy: SortBy, ascending: Boolean, natural: Boolean, stableSeed: Long): List<ZipItem> {
        if (sortBy == SortBy.RANDOM) return sortedBy { (it.path.hashCode().toLong() xor stableSeed) }
        val comparator = compareBy<ZipItem> {
            when (sortBy) {
                SortBy.NAME -> if (natural) naturalKey(it.name) else it.name.lowercase()
                SortBy.DATE_MODIFIED, SortBy.DATE_CREATED -> it.lastModified.toString().padStart(20, '0')
                SortBy.SIZE -> it.size.toString().padStart(20, '0')
                SortBy.IMAGE_COUNT -> it.imageCount.toString().padStart(10, '0')
                SortBy.RANDOM -> it.path
            }
        }
        return if (ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
    }

    private fun Volume.matchesFilter(filter: VolumeFilter) = when (filter) {
        VolumeFilter.ALL -> true
        VolumeFilter.INTERNAL -> id == "primary"
        VolumeFilter.SD -> id.startsWith("sdcard:")
        VolumeFilter.USB -> id.startsWith("usb:")
    }

    private fun ZipItem.matchesFilter(filter: VolumeFilter) = when (filter) {
        VolumeFilter.ALL -> true
        VolumeFilter.INTERNAL -> volumeId == "primary"
        VolumeFilter.SD -> volumeId.startsWith("sdcard:")
        VolumeFilter.USB -> volumeId.startsWith("usb:")
    }

    private fun openArchiveStream(zip: ZipItem): InputStream? = if (zip.isSaf) app.contentResolver.openInputStream(Uri.parse(zip.path)) else FileInputStream(File(zip.path))
    private fun invalidatePath(path: String) { thumbsDir.listFiles()?.filter { it.name.contains(path.hashCode().toString()) }?.forEach(File::delete); memoryCache.evictAll() }
    private fun sweepThumbnailCache(live: Set<String>) { if (live.isEmpty()) return; thumbsDir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 7 * 24 * 60 * 60 * 1000L }?.forEach(File::delete) }
    private fun thumbnailKey(path: String, modified: Long, size: Long, px: Int) = (path + modified + size + px).hashCode().toUInt().toString(16)
    private fun thumbnailFile(key: String) = File(thumbsDir, "$key.jpg")
    private fun safVolumeId(uri: String) = "saf:${uri.hashCode().toUInt().toString(16)}"
    private fun String.isImageEntry(): Boolean = !startsWith("__MACOSX/") && !substringAfterLast('/').startsWith('.') && substringAfterLast('.').lowercase() in IMAGE_EXT
    private fun ZipEntry.toItem() = ZipEntryItem(name, name.substringAfterLast('/'), name.isImageEntry(), size.coerceAtLeast(0), compressedSize.coerceAtLeast(0), folderPath = name.substringBeforeLast('/', ""))
    private fun File.canonicalOrAbsolute() = runCatching { canonicalPath }.getOrDefault(absolutePath)
    /**
     * Downsamples to the smallest power-of-two step that still covers [targetPx] on the longest
     * edge. Sampling past the target is what made frames look soft, so the loop stops one step
     * early and lets the GPU do the final, filtered scale.
     */
    private fun decodeSampled(bytes: ByteArray, targetPx: Int, highQuality: Boolean = false): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= targetPx || bounds.outHeight / (sample * 2) >= targetPx
        ) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (highQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
            ?: runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample * 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }.getOrNull()
    }
    private fun mimeFor(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; else -> "image/jpeg"
    }
}
