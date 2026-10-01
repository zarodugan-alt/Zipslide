package com.example.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.example.data.model.StorageVolumeInfo
import com.example.data.model.Volume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Single authority for mounted-volume state. The broadcast receiver calls the two notification
 * methods below; no UI owns mount state and an unmounted volume keeps its Room cache intact.
 */
class VolumeRepository(private val app: Application) {
    private val _volumes = MutableStateFlow<List<Volume>>(emptyList())
    private val _uiVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    private val _mountChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val unmountedRoots = mutableSetOf<String>()

    /** Contract-facing representation. */
    fun observeVolumes(): Flow<List<Volume>> = _volumes.asStateFlow()
    fun currentVolumes(): List<Volume> = _volumes.value
    fun onMountChanged(): Flow<Unit> = _mountChanges.asSharedFlow()

    /** Compatibility projection used by the existing Compose layout. */
    val volumes = _uiVolumes.asStateFlow()
    val currentUiVolumes: List<StorageVolumeInfo> get() = _uiVolumes.value

    init { refreshVolumes() }

    fun refreshVolumes() {
        val discovered = linkedMapOf<String, Volume>()
        val primary = Environment.getExternalStorageDirectory()
        addVolume(discovered, primary, id = "primary", label = "Internal storage", removable = false)

        val storageManager = app.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageManager.storageVolumes.forEachIndexed { index, storageVolume ->
                val root = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) storageVolume.directory else {
                    runCatching {
                        storageVolume.javaClass.getMethod("getPathFile").invoke(storageVolume) as? File
                    }.getOrNull()
                }
                val removable = storageVolume.isRemovable
                val description = storageVolume.getDescription(app).orEmpty()
                val canonical = root?.canonicalOrAbsolute().orEmpty()
                val id = when {
                    storageVolume.isPrimary -> "primary"
                    description.contains("usb", ignoreCase = true) -> "usb:${canonical.stableSuffix()}"
                    else -> "sdcard:${canonical.stableSuffix(index)}"
                }
                addVolume(discovered, root, id, description.ifBlank {
                    if (removable) "SD card" else "Internal storage"
                }, removable, storageVolume.state == Environment.MEDIA_MOUNTED)
            }
        }

        // OEMs occasionally omit removable volumes from StorageManager. The app-specific roots
        // are reliable enough to identify them; climb to the storage root and dedupe canonically.
        app.getExternalFilesDirs(null).forEachIndexed { index, appSpecificDir ->
            var root = appSpecificDir
            repeat(4) { root = root?.parentFile }
            if (root != null) {
                val canonical = root.canonicalOrAbsolute()
                if (discovered.values.none { it.root?.canonicalOrAbsolute() == canonical }) {
                    val removable = index > 0
                    addVolume(
                        discovered,
                        root,
                        if (removable) "sdcard:${canonical.stableSuffix(index)}" else "primary",
                        if (removable) "SD card" else "Internal storage",
                        removable
                    )
                }
            }
        }
        _volumes.value = discovered.values.toList()
        _uiVolumes.value = _volumes.value.map(::toUiVolume)
    }

    private fun addVolume(
        into: MutableMap<String, Volume>,
        root: File?,
        id: String,
        label: String,
        removable: Boolean,
        platformMounted: Boolean = true
    ) {
        val canonical = root?.canonicalOrAbsolute() ?: return
        if (into.values.any { it.root?.canonicalOrAbsolute() == canonical }) return
        val mounted = platformMounted && canonical !in unmountedRoots && (root.exists() || id == "primary")
        into[id] = Volume(
            id = id,
            label = label,
            root = root,
            safUri = null,
            isRemovable = removable,
            isMounted = mounted,
            isReadable = mounted && root.canRead()
        )
    }

    fun volumeFor(path: String): Volume? = _volumes.value
        .filter { volume -> volume.root?.let { path.startsWith(it.absolutePath) } == true }
        .maxByOrNull { it.root?.absolutePath?.length ?: 0 }

    fun isPathMounted(path: String): Boolean = volumeFor(path)?.isMounted ?: !unmountedRoots.any(path::startsWith)

    fun onMediaMounted(path: String?) {
        path?.let { unmountedRoots.remove(File(it).canonicalOrAbsolute()) }
        refreshVolumes()
        _mountChanges.tryEmit(Unit)
    }

    fun onMediaUnmounted(path: String?) {
        path?.let { unmountedRoots += File(it).canonicalOrAbsolute() }
        refreshVolumes()
        _mountChanges.tryEmit(Unit)
    }

    private fun toUiVolume(volume: Volume): StorageVolumeInfo {
        val root = volume.root
        val total = root?.totalSpace ?: 0L
        val free = root?.freeSpace ?: 0L
        val isUsb = volume.id.startsWith("usb:")
        return StorageVolumeInfo(
            id = volume.id,
            name = volume.label,
            rootFile = root,
            uriString = volume.safUri?.toString(),
            isMounted = volume.isMounted,
            isInternal = volume.id == "primary",
            isSdCard = volume.id.startsWith("sdcard:"),
            isUsb = isUsb,
            totalBytes = total,
            freeBytes = free,
            usedBytes = (total - free).coerceAtLeast(0),
            readableDirect = root?.canRead() == true,
            readableSaf = volume.safUri != null
        )
    }

    private fun File.canonicalOrAbsolute() = runCatching { canonicalPath }.getOrDefault(absolutePath)
    private fun String.stableSuffix(fallback: Int = 0) =
        takeLastWhile { it.isLetterOrDigit() }.takeIf { it.isNotBlank() } ?: hashCode().toUInt().toString(16).ifBlank { fallback.toString() }
}
