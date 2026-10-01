package com.example.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.example.data.model.StorageVolumeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VolumeRepository(private val context: Context) {

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes.asStateFlow()

    private val unmountedRoots = mutableSetOf<String>()

    init {
        refreshVolumes()
    }

    fun refreshVolumes() {
        val discovered = mutableListOf<StorageVolumeInfo>()
        val seenCanonical = mutableSetOf<String>()

        // 1. Primary external storage (Internal)
        val primaryDir = Environment.getExternalStorageDirectory()
        if (primaryDir != null && primaryDir.exists()) {
            val canon = runCatching { primaryDir.canonicalPath }.getOrDefault(primaryDir.absolutePath)
            seenCanonical.add(canon)
            val total = primaryDir.totalSpace
            val free = primaryDir.freeSpace
            discovered.add(
                StorageVolumeInfo(
                    id = "internal",
                    name = "Internal storage",
                    rootFile = primaryDir,
                    isMounted = !unmountedRoots.contains(canon),
                    isInternal = true,
                    isSdCard = false,
                    isUsb = false,
                    totalBytes = total,
                    freeBytes = free,
                    usedBytes = (total - free).coerceAtLeast(0L),
                    readableDirect = primaryDir.canRead()
                )
            )
        }

        // 2. StorageManager storageVolumes (API 24+)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageVolumes = storageManager.storageVolumes
            for ((index, vol) in storageVolumes.withIndex()) {
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    // Reflection fallback on older APIs
                    runCatching {
                        val method = vol.javaClass.getMethod("getPathFile")
                        method.invoke(vol) as? File
                    }.getOrNull()
                }

                if (dir != null && dir.exists()) {
                    val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
                    if (seenCanonical.add(canon)) {
                        val isRemovable = vol.isRemovable
                        val isPrimary = vol.isPrimary
                        val name = vol.getDescription(context) ?: if (isRemovable) "SD card" else "Internal storage"
                        val total = dir.totalSpace
                        val free = dir.freeSpace
                        val isMounted = vol.state == Environment.MEDIA_MOUNTED && !unmountedRoots.contains(canon)
                        discovered.add(
                            StorageVolumeInfo(
                                id = vol.uuid ?: "vol_$index",
                                name = name,
                                rootFile = dir,
                                isMounted = isMounted,
                                isInternal = isPrimary,
                                isSdCard = isRemovable && !name.contains("USB", ignoreCase = true),
                                isUsb = name.contains("USB", ignoreCase = true),
                                totalBytes = total,
                                freeBytes = free,
                                usedBytes = (total - free).coerceAtLeast(0L),
                                readableDirect = dir.canRead()
                            )
                        )
                    }
                }
            }
        }

        // 3. Fallback discovery via context.getExternalFilesDirs(null)
        val extDirs = context.getExternalFilesDirs(null)
        for ((idx, extDir) in extDirs.withIndex()) {
            if (extDir == null) continue
            var f: File? = extDir
            repeat(4) {
                f = f?.parentFile
            }
            val root = f
            if (root != null && root.exists()) {
                val canon = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
                if (seenCanonical.add(canon)) {
                    val isSd = idx > 0
                    val name = if (isSd) "SD card (${root.name})" else "Storage ($idx)"
                    val total = root.totalSpace
                    val free = root.freeSpace
                    discovered.add(
                        StorageVolumeInfo(
                            id = "ext_$idx",
                            name = name,
                            rootFile = root,
                            isMounted = !unmountedRoots.contains(canon),
                            isInternal = !isSd,
                            isSdCard = isSd,
                            isUsb = false,
                            totalBytes = total,
                            freeBytes = free,
                            usedBytes = (total - free).coerceAtLeast(0L),
                            readableDirect = root.canRead()
                        )
                    )
                }
            }
        }

        if (discovered.isEmpty() && primaryDir != null) {
            discovered.add(
                StorageVolumeInfo(
                    id = "internal",
                    name = "Internal storage",
                    rootFile = primaryDir,
                    isMounted = true,
                    isInternal = true,
                    readableDirect = primaryDir.canRead()
                )
            )
        }

        _volumes.value = discovered
    }

    fun onMediaMounted(path: String?) {
        if (path != null) {
            unmountedRoots.remove(path)
        }
        refreshVolumes()
    }

    fun onMediaUnmounted(path: String?) {
        if (path != null) {
            unmountedRoots.add(path)
        }
        refreshVolumes()
    }

    fun isPathMounted(path: String): Boolean {
        for (vol in _volumes.value) {
            val rootPath = vol.rootFile?.absolutePath ?: continue
            if (path.startsWith(rootPath) && !vol.isMounted) {
                return false
            }
        }
        return true
    }
}
