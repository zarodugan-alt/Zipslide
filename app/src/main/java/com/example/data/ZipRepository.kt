package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.ZipMetaDao
import com.example.data.local.ZipMetaEntity
import com.example.data.model.BrowserFilter
import com.example.data.model.SortBy
import com.example.data.model.StorageVolumeInfo
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.util.NaturalOrderComparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ZipRepository(
    private val context: Context,
    private val zipMetaDao: ZipMetaDao,
    private val volumeRepository: VolumeRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    private val _rawZips = MutableStateFlow<List<ZipItem>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Combined Flow containing sorted and filtered items for UI
    val zipsFlow = combine(
        _rawZips,
        settingsRepository.settingsFlow,
        volumeRepository.volumes
    ) { zips, settings, volumes ->
        val volumeMap = volumes.associateBy { it.id }

        // Update mount states according to active volumes
        val processedZips = zips.map { zip ->
            val vol = volumeMap[zip.volumeId]
            val isMounted = vol?.isMounted ?: volumeRepository.isPathMounted(zip.path)
            zip.copy(isMounted = isMounted)
        }

        // Apply storage source filter
        val sourceFiltered = when (settings.storageSource) {
            "internal" -> processedZips.filter { it.volumeId == "internal" || !it.volumeId.contains("sd", ignoreCase = true) }
            "sd" -> processedZips.filter { it.volumeId.contains("sd", ignoreCase = true) }
            else -> processedZips
        }

        // Apply sort
        sortZips(sourceFiltered, settings.sortBy, settings.sortAscending, settings.naturalSort)
    }

    init {
        // Instant cold start: Load metadata from Room immediately
        scope.launch {
            loadFromDbCache()
            ensureSampleZipsExist()
            triggerRescan()
        }
    }

    private suspend fun loadFromDbCache() = withContext(Dispatchers.IO) {
        val cachedEntities = zipMetaDao.getAllMetaList()
        if (cachedEntities.isNotEmpty()) {
            val cachedZips = cachedEntities.map { entity ->
                val file = if (!entity.isSaf) File(entity.path) else null
                val exists = file?.exists() ?: true
                val thumbFile = getCacheFileForPath(entity.path, entity.lastModified, file?.length() ?: 0L, 512)
                ZipItem(
                    path = entity.path,
                    name = file?.name ?: entity.path.substringAfterLast('/'),
                    size = entity.cachedTotalSize,
                    lastModified = entity.lastModified,
                    volumeId = entity.volumeId,
                    volumeName = if (entity.volumeId == "internal") "Internal storage" else "SD card",
                    isMounted = exists && volumeRepository.isPathMounted(entity.path),
                    isSaf = entity.isSaf,
                    imageCount = entity.cachedImageCount,
                    hasCover = entity.hasCover,
                    coverEntryName = entity.coverEntryName,
                    thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
                    isFavorite = entity.isFavorite,
                    lastFrameIndex = entity.lastFrameIndex,
                    isReadOnly = file != null && (!file.canWrite() || !file.parentFile.canWrite()),
                    parentFolder = file?.parent ?: ""
                )
            }
            _rawZips.value = cachedZips
        }
    }

    fun triggerRescan() {
        scope.launch {
            scanStorage()
        }
    }

    fun updateMountStates() {
        val current = _rawZips.value
        _rawZips.value = current.map { zip ->
            zip.copy(isMounted = volumeRepository.isPathMounted(zip.path))
        }
    }

    private suspend fun scanStorage() = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true

        try {
            val settings = settingsRepository.settingsFlow.first()
            val volumes = volumeRepository.volumes.value
            val foundZips = mutableListOf<ZipItem>()
            val canonicalSeen = mutableSetOf<String>()

            // 1. Scan direct file storage roots
            val roots = mutableListOf<File>()
            for (vol in volumes) {
                if (vol.rootFile != null && vol.rootFile.exists() && vol.isMounted && vol.readableDirect) {
                    roots.add(vol.rootFile)
                }
            }

            // Also check custom scan folder if specified
            if (settings.customScanFolderUri != null) {
                runCatching {
                    val customUri = Uri.parse(settings.customScanFolderUri)
                    if (customUri.scheme == "file") {
                        val customFile = File(customUri.path ?: "")
                        if (customFile.exists() && customFile.isDirectory) {
                            roots.add(customFile)
                        }
                    } else if (customUri.scheme == "content") {
                        // Scan via SAF DocumentFile
                        scanSafDirectory(customUri, foundZips, canonicalSeen, settings.thumbnailPx)
                    }
                }
            }

            // Limit scan parallelism to 4 concurrent
            val semaphore = Semaphore(4)
            val existingMeta = zipMetaDao.getAllMetaList().associateBy { it.path }

            for (root in roots) {
                scanDirectoryRecursively(
                    dir = root,
                    volumeId = if (root.absolutePath.contains("emulated", ignoreCase = true)) "internal" else "sd",
                    volumeName = if (root.absolutePath.contains("emulated", ignoreCase = true)) "Internal storage" else "SD card",
                    includeSubfolders = settings.includeSubfolders,
                    showHidden = settings.showHidden,
                    foundZips = foundZips,
                    canonicalSeen = canonicalSeen,
                    existingMeta = existingMeta,
                    thumbnailPx = settings.thumbnailPx,
                    semaphore = semaphore
                )
            }

            // Update state
            _rawZips.value = foundZips

            // Persist to Room for instant cold starts
            val entities = foundZips.map { zip ->
                val old = existingMeta[zip.path]
                ZipMetaEntity(
                    path = zip.path,
                    isFavorite = old?.isFavorite ?: false,
                    lastFrameIndex = old?.lastFrameIndex ?: 0,
                    customTitle = old?.customTitle,
                    cachedImageCount = zip.imageCount,
                    cachedTotalSize = zip.size,
                    hasCover = zip.hasCover,
                    coverEntryName = zip.coverEntryName,
                    lastModified = zip.lastModified,
                    volumeId = zip.volumeId,
                    isSaf = zip.isSaf
                )
            }
            zipMetaDao.insertAll(entities)

            // Sweep obsolete thumbnails
            sweepThumbnailCache(foundZips.map { it.path }.toSet())

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun scanDirectoryRecursively(
        dir: File,
        volumeId: String,
        volumeName: String,
        includeSubfolders: Boolean,
        showHidden: Boolean,
        foundZips: MutableList<ZipItem>,
        canonicalSeen: MutableSet<String>,
        existingMeta: Map<String, ZipMetaEntity>,
        thumbnailPx: Int,
        semaphore: Semaphore
    ) {
        if (!dir.exists() || !dir.canRead()) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            val name = file.name
            // Always skip: Android/, .thumbnails, dotdirs
            if (name.equals("Android", ignoreCase = true) ||
                name.equals(".thumbnails", ignoreCase = true) ||
                (name.startsWith(".") && !showHidden)
            ) {
                continue
            }

            if (file.isDirectory) {
                if (includeSubfolders) {
                    scanDirectoryRecursively(
                        dir = file,
                        volumeId = volumeId,
                        volumeName = volumeName,
                        includeSubfolders = includeSubfolders,
                        showHidden = showHidden,
                        foundZips = foundZips,
                        canonicalSeen = canonicalSeen,
                        existingMeta = existingMeta,
                        thumbnailPx = thumbnailPx,
                        semaphore = semaphore
                    )
                }
            } else if (name.endsWith(".zip", ignoreCase = true)) {
                val canon = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                if (canonicalSeen.add(canon)) {
                    val zipItem = inspectZipFile(
                        file = file,
                        volumeId = volumeId,
                        volumeName = volumeName,
                        existingMeta = existingMeta[canon],
                        thumbnailPx = thumbnailPx,
                        semaphore = semaphore
                    )
                    foundZips.add(zipItem)
                }
            }
        }
    }

    private suspend fun scanSafDirectory(
        treeUri: Uri,
        foundZips: MutableList<ZipItem>,
        canonicalSeen: MutableSet<String>,
        thumbnailPx: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val lastModified = cursor.getLong(modCol)
                    if (name.endsWith(".zip", ignoreCase = true)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val uriStr = docUri.toString()
                        if (canonicalSeen.add(uriStr)) {
                            val zipItem = inspectSafZip(docUri, name, size, lastModified, thumbnailPx)
                            foundZips.add(zipItem)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inspect direct zip file using java.util.zip.ZipFile
     * One bad zip never breaks the scan: catches per-zip exceptions cleanly.
     */
    private suspend fun inspectZipFile(
        file: File,
        volumeId: String,
        volumeName: String,
        existingMeta: ZipMetaEntity?,
        thumbnailPx: Int,
        semaphore: Semaphore
    ): ZipItem = withContext(Dispatchers.IO) {
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val lastModified = file.lastModified()
        val fileLength = file.length()
        val thumbFile = getCacheFileForPath(path, lastModified, fileLength, thumbnailPx)

        var imageCount = existingMeta?.cachedImageCount ?: 0
        var hasCover = existingMeta?.hasCover ?: true
        var coverEntryName: String? = existingMeta?.coverEntryName
        var isCorrupt = false
        var isEncrypted = false
        var isZeroImages = false

        // If thumbnail exists and cache is valid, skip zip inspection
        val needInspect = !thumbFile.exists() || existingMeta == null || existingMeta.lastModified != lastModified

        if (needInspect) {
            semaphore.withPermit {
                try {
                    ZipFile(file).use { zip ->
                        val entries = mutableListOf<String>()
                        val enumeration = zip.entries()
                        while (enumeration.hasMoreElements()) {
                            val entry = enumeration.nextElement()
                            val entryName = entry.name
                            if (!entry.isDirectory && isValidImageEntry(entryName)) {
                                entries.add(entryName)
                            }
                        }

                        imageCount = entries.size
                        isZeroImages = entries.isEmpty()

                        if (entries.isNotEmpty()) {
                            val (resolvedCover, coverFound) = resolveCoverEntry(entries)
                            coverEntryName = resolvedCover
                            hasCover = coverFound

                            if (resolvedCover != null) {
                                val coverZipEntry = zip.getEntry(resolvedCover)
                                if (coverZipEntry != null) {
                                    zip.getInputStream(coverZipEntry).use { input ->
                                        decodeAndSaveThumbnail(input, thumbFile, thumbnailPx)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message?.lowercase() ?: ""
                    if (msg.contains("encrypted") || msg.contains("password")) {
                        isEncrypted = true
                    } else {
                        isCorrupt = true
                    }
                }
            }
        }

        val canWrite = file.canWrite() && (file.parentFile?.canWrite() != false)

        ZipItem(
            path = path,
            name = file.name,
            size = fileLength,
            lastModified = lastModified,
            volumeId = volumeId,
            volumeName = volumeName,
            isMounted = true,
            isSaf = false,
            imageCount = imageCount,
            hasCover = hasCover,
            coverEntryName = coverEntryName,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            isEncrypted = isEncrypted,
            isCorrupt = isCorrupt,
            isZeroImages = isZeroImages,
            isFavorite = existingMeta?.isFavorite ?: false,
            lastFrameIndex = existingMeta?.lastFrameIndex ?: 0,
            isReadOnly = !canWrite,
            parentFolder = file.parent ?: ""
        )
    }

    /**
     * Inspect SAF zip using ZipInputStream (Secondary read path for restricted SD cards)
     */
    private suspend fun inspectSafZip(
        docUri: Uri,
        name: String,
        fileLength: Long,
        lastModified: Long,
        thumbnailPx: Int
    ): ZipItem = withContext(Dispatchers.IO) {
        val uriStr = docUri.toString()
        val thumbFile = getCacheFileForPath(uriStr, lastModified, fileLength, thumbnailPx)

        var imageCount = 0
        var hasCover = true
        var coverEntryName: String? = null
        var isCorrupt = false
        var isZeroImages = false

        if (!thumbFile.exists()) {
            try {
                context.contentResolver.openInputStream(docUri)?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zis ->
                        val entries = mutableListOf<String>()
                        var entry = zis.nextEntry
                        val entryBytesMap = mutableMapOf<String, ByteArray>()

                        while (entry != null) {
                            val entryName = entry.name
                            if (!entry.isDirectory && isValidImageEntry(entryName)) {
                                entries.add(entryName)
                                // Keep bytes of candidates for 1.jpg resolution
                                val base = getBasename(entryName)
                                if (base.startsWith("1.", ignoreCase = true) || entries.size <= 2) {
                                    val buffer = ByteArrayOutputStream()
                                    zis.copyTo(buffer)
                                    entryBytesMap[entryName] = buffer.toByteArray()
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }

                        imageCount = entries.size
                        isZeroImages = entries.isEmpty()

                        if (entries.isNotEmpty()) {
                            val (resolvedCover, coverFound) = resolveCoverEntry(entries)
                            coverEntryName = resolvedCover
                            hasCover = coverFound

                            if (resolvedCover != null) {
                                val bytes = entryBytesMap[resolvedCover]
                                if (bytes != null) {
                                    decodeAndSaveThumbnail(bytes.inputStream(), thumbFile, thumbnailPx)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                isCorrupt = true
            }
        }

        ZipItem(
            path = uriStr,
            name = name,
            size = fileLength,
            lastModified = lastModified,
            volumeId = "sd",
            volumeName = "SD card",
            isMounted = true,
            isSaf = true,
            imageCount = imageCount,
            hasCover = hasCover,
            coverEntryName = coverEntryName,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            isCorrupt = isCorrupt,
            isZeroImages = isZeroImages,
            isReadOnly = false,
            parentFolder = ""
        )
    }

    /**
     * The 1.jpg Rule resolution:
     * 1. Entry with basename exactly 1.jpg (case-insensitive), prefer root, then shallowest depth
     * 2. 1.jpeg -> 3. 1.png -> 4. 1.webp -> 5. 1.gif -> 6. any image whose basename starts with 1.
     * 7. Fallback: natural-sorted first image entry
     */
    fun resolveCoverEntry(imageEntries: List<String>): Pair<String?, Boolean> {
        if (imageEntries.isEmpty()) return Pair(null, false)

        // Rule 1: Basename exactly 1.jpg (case-insensitive), prefer shallowest depth
        val exactJpgs = imageEntries.filter { getBasename(it).equals("1.jpg", ignoreCase = true) }
        if (exactJpgs.isNotEmpty()) {
            val shallowest = exactJpgs.minWithOrNull(compareBy({ getDepth(it) }, { NaturalOrderComparator.compare(it, it) }))
            return Pair(shallowest, true)
        }

        // Rule 2: 1.jpeg
        val exactJpegs = imageEntries.filter { getBasename(it).equals("1.jpeg", ignoreCase = true) }
        if (exactJpegs.isNotEmpty()) {
            return Pair(exactJpegs.minByOrNull { getDepth(it) }, true)
        }

        // Rule 3: 1.png
        val exactPngs = imageEntries.filter { getBasename(it).equals("1.png", ignoreCase = true) }
        if (exactPngs.isNotEmpty()) {
            return Pair(exactPngs.minByOrNull { getDepth(it) }, true)
        }

        // Rule 4: 1.webp
        val exactWebps = imageEntries.filter { getBasename(it).equals("1.webp", ignoreCase = true) }
        if (exactWebps.isNotEmpty()) {
            return Pair(exactWebps.minByOrNull { getDepth(it) }, true)
        }

        // Rule 5: 1.gif
        val exactGifs = imageEntries.filter { getBasename(it).equals("1.gif", ignoreCase = true) }
        if (exactGifs.isNotEmpty()) {
            return Pair(exactGifs.minByOrNull { getDepth(it) }, true)
        }

        // Rule 6: Any image entry whose basename starts with 1.
        val startsWith1 = imageEntries.filter { getBasename(it).startsWith("1.", ignoreCase = true) }
        if (startsWith1.isNotEmpty()) {
            val shallowest = startsWith1.minWithOrNull(compareBy({ getDepth(it) }, { NaturalOrderComparator.compare(it, it) }))
            return Pair(shallowest, true)
        }

        // Rule 7: Fallback: natural-sorted first image entry (Missing 1.jpg -> hasCover = false)
        val sortedFallback = imageEntries.sortedWith(NaturalOrderComparator)
        return Pair(sortedFallback.firstOrNull(), false)
    }

    private fun isValidImageEntry(entryName: String): Boolean {
        // Always skip: __MACOSX/, entries whose basename starts with '.', directory entries
        if (entryName.startsWith("__MACOSX", ignoreCase = true) || entryName.contains("/__MACOSX", ignoreCase = true)) {
            return false
        }
        val basename = getBasename(entryName)
        if (basename.startsWith(".")) {
            return false
        }
        val lower = basename.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
               lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
    }

    private fun getBasename(path: String): String {
        return path.substringAfterLast('/')
    }

    private fun getDepth(path: String): Int {
        return path.count { it == '/' }
    }

    private fun getCacheFileForPath(path: String, lastModified: Long, length: Long, thumbnailPx: Int): File {
        val hash = (path + lastModified + length + thumbnailPx).hashCode()
        return File(thumbDir, "thumb_${Math.abs(hash.toLong())}.jpg")
    }

    private fun decodeAndSaveThumbnail(input: InputStream, outputFile: File, targetPx: Int) {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return

        // 1. inJustDecodeBounds
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return

        // 2. Compute inSampleSize
        var inSampleSize = 1
        val maxDim = Math.max(origWidth, origHeight)
        while ((maxDim / (inSampleSize * 2)) >= targetPx) {
            inSampleSize *= 2
        }

        // 3. Decode at downsampled size
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return

        // 4. Save to cache
        runCatching {
            FileOutputStream(outputFile).use { out ->
                decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }
    }

    private fun sweepThumbnailCache(activeZipPaths: Set<String>) {
        val files = thumbDir.listFiles() ?: return
        val activeZipFiles = activeZipPaths.map { File(it) }.filter { it.exists() }
        val activeHashes = activeZipFiles.map { file ->
            "thumb_${Math.abs((file.canonicalPath + file.lastModified() + file.length() + 512).hashCode().toLong())}.jpg"
        }.toSet()

        for (file in files) {
            // Keep if recently touched or matches active hash
            if (!activeHashes.contains(file.name) && System.currentTimeMillis() - file.lastModified() > 86400000L) {
                file.delete()
            }
        }
    }

    suspend fun clearThumbnailCache() = withContext(Dispatchers.IO) {
        thumbDir.listFiles()?.forEach { it.delete() }
        triggerRescan()
    }

    suspend fun toggleFavorite(path: String) = withContext(Dispatchers.IO) {
        val current = _rawZips.value.find { it.path == path } ?: return@withContext
        val newFav = !current.isFavorite
        zipMetaDao.updateFavorite(path, newFav)
        _rawZips.value = _rawZips.value.map {
            if (it.path == path) it.copy(isFavorite = newFav) else it
        }
    }

    suspend fun updateLastFrame(path: String, frameIndex: Int) = withContext(Dispatchers.IO) {
        zipMetaDao.updateLastFrame(path, frameIndex)
        _rawZips.value = _rawZips.value.map {
            if (it.path == path) it.copy(lastFrameIndex = frameIndex) else it
        }
    }

    suspend fun renameZip(zip: ZipItem, newName: String): Result<ZipItem> = withContext(Dispatchers.IO) {
        if (zip.isSaf) {
            return@withContext Result.failure(IOException("SAF rename not supported on this volume"))
        }
        val file = File(zip.path)
        if (!file.exists()) return@withContext Result.failure(IOException("File not found"))
        if (!file.canWrite() || file.parentFile?.canWrite() == false) {
            return@withContext Result.failure(IOException("This folder is read-only"))
        }

        val formattedName = if (newName.endsWith(".zip", ignoreCase = true)) newName else "$newName.zip"
        val targetFile = File(file.parentFile, formattedName)
        if (targetFile.exists()) {
            return@withContext Result.failure(IOException("A file named '$formattedName' already exists"))
        }

        val success = file.renameTo(targetFile)
        if (!success) {
            return@withContext Result.failure(IOException("Failed to rename file"))
        }

        // Delete old metadata and invalidate old thumbnail
        zipMetaDao.deleteByPath(zip.path)
        val oldThumb = zip.thumbnailPath?.let { File(it) }
        oldThumb?.delete()

        triggerRescan()
        Result.success(zip.copy(path = targetFile.canonicalPath, name = targetFile.name))
    }

    suspend fun deleteZip(zip: ZipItem): Result<Unit> = withContext(Dispatchers.IO) {
        if (zip.isSaf) {
            try {
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(zip.path))
                if (deleted) {
                    zipMetaDao.deleteByPath(zip.path)
                    zip.thumbnailPath?.let { File(it).delete() }
                    _rawZips.value = _rawZips.value.filter { it.path != zip.path }
                    return@withContext Result.success(Unit)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
            return@withContext Result.failure(IOException("Could not delete via SAF"))
        }

        val file = File(zip.path)
        if (!file.exists()) {
            zipMetaDao.deleteByPath(zip.path)
            _rawZips.value = _rawZips.value.filter { it.path != zip.path }
            return@withContext Result.success(Unit)
        }

        if (!file.canWrite() || file.parentFile?.canWrite() == false) {
            return@withContext Result.failure(IOException("This folder is read-only"))
        }

        val deleted = file.delete()
        if (deleted) {
            zipMetaDao.deleteByPath(zip.path)
            zip.thumbnailPath?.let { File(it).delete() }
            _rawZips.value = _rawZips.value.filter { it.path != zip.path }
            return@withContext Result.success(Unit)
        } else {
            return@withContext Result.failure(IOException("Failed to delete file"))
        }
    }

    /**
     * Get sorted entries of a zip (natural sort, 2.jpg before 10.jpg)
     */
    suspend fun getZipEntries(zip: ZipItem): List<ZipEntryItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ZipEntryItem>()
        try {
            if (zip.isSaf) {
                context.contentResolver.openInputStream(Uri.parse(zip.path))?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory && !name.startsWith("__MACOSX")) {
                                val isImg = isValidImageEntry(name)
                                val base = getBasename(name)
                                val folder = name.substringBeforeLast('/', "")
                                result.add(
                                    ZipEntryItem(
                                        entryPath = name,
                                        basename = base,
                                        isImage = isImg,
                                        size = entry.size.coerceAtLeast(0L),
                                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                        folderPath = folder
                                    )
                                )
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                val file = File(zip.path)
                if (file.exists()) {
                    ZipFile(file).use { zipFile ->
                        val enumeration = zipFile.entries()
                        while (enumeration.hasMoreElements()) {
                            val entry = enumeration.nextElement()
                            val name = entry.name
                            if (!entry.isDirectory && !name.startsWith("__MACOSX")) {
                                val isImg = isValidImageEntry(name)
                                val base = getBasename(name)
                                val folder = name.substringBeforeLast('/', "")
                                result.add(
                                    ZipEntryItem(
                                        entryPath = name,
                                        basename = base,
                                        isImage = isImg,
                                        size = entry.size.coerceAtLeast(0L),
                                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                        folderPath = folder
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Natural sort all entries
        result.sortedWith(compareBy({ !it.isImage }, { NaturalOrderComparator.compare(it.entryPath, it.entryPath) }))
    }

    /**
     * Load a single frame bitmap for slideshow or viewer (downsampled to ~1600px, never full raw)
     */
    suspend fun loadFrameBitmap(zipPath: String, entryName: String, maxDim: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (zipPath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(zipPath))?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == entryName) {
                                val bytes = zis.readBytes()
                                return@withContext decodeSampledBitmap(bytes, maxDim)
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                val file = File(zipPath)
                if (file.exists()) {
                    ZipFile(file).use { zipFile ->
                        val entry = zipFile.getEntry(entryName) ?: return@withContext null
                        zipFile.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            return@withContext decodeSampledBitmap(bytes, maxDim)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxTargetDim: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var sampleSize = 1
        val maxOriginal = Math.max(w, h)
        while ((maxOriginal / (sampleSize * 2)) >= maxTargetDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    /**
     * Extract images from zip to target directory
     * Defaults to the same volume as the source zip!
     */
    suspend fun extractImages(
        zip: ZipItem,
        destDir: File? = null,
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = destDir ?: run {
            val srcFile = File(zip.path)
            val parent = srcFile.parentFile ?: context.getExternalFilesDir("Extracted") ?: context.filesDir
            val folderName = zip.name.substringBeforeLast('.') + "_extracted"
            File(parent, folderName).apply { mkdirs() }
        }

        if (!targetDir.canWrite()) {
            return@withContext Result.failure(IOException("This folder is read-only"))
        }

        try {
            val entries = getZipEntries(zip).filter { it.isImage }
            val total = entries.size
            if (total == 0) {
                return@withContext Result.failure(IOException("No images found in zip"))
            }

            if (zip.isSaf) {
                context.contentResolver.openInputStream(Uri.parse(zip.path))?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zis ->
                        var entry = zis.nextEntry
                        var extractedCount = 0
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory && isValidImageEntry(name)) {
                                val base = getBasename(name)
                                val outFile = File(targetDir, base)
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                extractedCount++
                                onProgress(extractedCount, total, base)
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                ZipFile(File(zip.path)).use { zipFile ->
                    for ((index, item) in entries.withIndex()) {
                        val entry = zipFile.getEntry(item.entryPath) ?: continue
                        val outFile = File(targetDir, item.basename)
                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        onProgress(index + 1, total, item.basename)
                    }
                }
            }
            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sortZips(
        list: List<ZipItem>,
        sortBy: SortBy,
        ascending: Boolean,
        naturalSort: Boolean
    ): List<ZipItem> {
        val comparator = Comparator<ZipItem> { a, b ->
            val result = when (sortBy) {
                SortBy.NAME -> {
                    if (naturalSort) NaturalOrderComparator.compare(a.name, b.name)
                    else a.name.compareTo(b.name, ignoreCase = true)
                }
                SortBy.MODIFIED -> a.lastModified.compareTo(b.lastModified)
                SortBy.CREATED -> a.lastModified.compareTo(b.lastModified)
                SortBy.SIZE -> a.size.compareTo(b.size)
                SortBy.IMAGE_COUNT -> a.imageCount.compareTo(b.imageCount)
                SortBy.RANDOM -> 0
            }
            if (ascending) result else -result
        }

        return if (sortBy == SortBy.RANDOM) list.shuffled() else list.sortedWith(comparator)
    }

    /**
     * Seeds initial sample slideshow zips so the user immediately experiences
     * 1.jpg cover resolution and fullscreen playback without hunting for files.
     */
    private suspend fun ensureSampleZipsExist() = withContext(Dispatchers.IO) {
        val targetDir = context.getExternalFilesDir("Slideshows") ?: File(context.filesDir, "Slideshows")
        targetDir.mkdirs()

        val sample1 = File(targetDir, "Nature_Expedition.zip")
        val sample2 = File(targetDir, "Architecture_Walk.zip")
        val sample3 = File(targetDir, "Vintage_Portraits.zip")

        if (!sample1.exists()) {
            createDemoZip(sample1, "Nature", hasOneJpg = true, count = 12)
        }
        if (!sample2.exists()) {
            createDemoZip(sample2, "Architecture", hasOneJpg = true, count = 15)
        }
        if (!sample3.exists()) {
            // Demo zip missing 1.jpg to showcase warning dot & fallback
            createDemoZip(sample3, "Vintage", hasOneJpg = false, count = 8)
        }
    }

    private fun createDemoZip(zipFile: File, title: String, hasOneJpg: Boolean, count: Int) {
        runCatching {
            FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    val startIndex = if (hasOneJpg) 1 else 2
                    for (i in startIndex..(startIndex + count - 1)) {
                        val entryName = if (i == 10 && hasOneJpg) "10.jpg" else "$i.jpg"
                        zos.putNextEntry(ZipEntry(entryName))

                        // Generate clean crisp demo image bitmap
                        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.RGB_565)
                        val canvas = Canvas(bmp)
                        val paint = Paint().apply { isAntiAlias = true }

                        // Background gradient-like color
                        val hue = (i * 35f + title.hashCode()) % 360f
                        val color = AndroidColor.HSVToColor(floatArrayOf(hue, 0.65f, 0.25f))
                        canvas.drawColor(color)

                        // Title and frame text
                        paint.color = AndroidColor.parseColor("#E8B458")
                        paint.textSize = 48f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("$title Slideshow", 400f, 260f, paint)

                        paint.color = AndroidColor.WHITE
                        paint.textSize = 36f
                        val frameLabel = if (i == 1 && hasOneJpg) "1.jpg (First Frame / Cover)" else "Frame #$i"
                        canvas.drawText(frameLabel, 400f, 340f, paint)

                        paint.color = AndroidColor.parseColor("#A0A0AB")
                        paint.textSize = 24f
                        canvas.drawText("Natural Sort Verification Test", 400f, 400f, paint)

                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, zos)
                        zos.closeEntry()
                        bmp.recycle()
                    }
                }
            }
        }
    }
}
