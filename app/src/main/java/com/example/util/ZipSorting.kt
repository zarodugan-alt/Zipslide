package com.example.util

import com.example.data.model.SortBy
import com.example.data.model.ZipItem

/**
 * The one and only browser ordering.
 *
 * It used to exist twice — once in the repository and once in the browser view model — with
 * padded-string comparisons that silently mis-ordered anything longer than the pad. Both call
 * sites now share these typed comparators.
 *
 * **This never touches the frames inside an archive.** Library order and frame order are separate
 * concerns; see `ZipRepository.playbackFrames`.
 */
fun List<ZipItem>.sortedForBrowser(
    sortBy: SortBy,
    ascending: Boolean,
    naturalNames: Boolean,
    seed: Long
): List<ZipItem> {
    if (sortBy == SortBy.RANDOM) {
        // Stable for a given seed so the grid does not reshuffle on every recomposition.
        return sortedBy { item -> (item.path.hashCode().toLong() * 31L) xor seed }
    }

    val comparator: Comparator<ZipItem> = when (sortBy) {
        SortBy.NAME -> compareBy { if (naturalNames) naturalKey(it.name) else it.name.lowercase() }
        SortBy.DATE_MODIFIED -> compareBy { it.lastModified }
        SortBy.DATE_CREATED -> compareBy { if (it.createdAt > 0L) it.createdAt else it.lastModified }
        SortBy.SIZE -> compareBy { it.size }
        SortBy.IMAGE_COUNT -> compareBy { it.imageCount }
        SortBy.LAST_VIEWED -> compareBy { it.lastViewedAt }
        SortBy.WATCH_COUNT -> compareBy { it.watchCount }
        SortBy.PROGRESS -> compareBy { it.progressFraction }
        SortBy.FOLDER -> compareBy<ZipItem> { it.parentFolder.lowercase() }
            .thenBy { naturalKey(it.name) }
        SortBy.RANDOM -> compareBy { it.path }
    }

    // A deterministic tie-break keeps equal keys (same size, same date, never viewed) stable.
    val stable = comparator.thenBy { naturalKey(it.name) }.thenBy { it.path }
    return if (ascending) sortedWith(stable) else sortedWith(stable.reversed())
}
