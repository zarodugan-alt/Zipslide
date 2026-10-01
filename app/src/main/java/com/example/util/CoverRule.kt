package com.example.util

/**
 * The single source of truth for ZipSlide's `1.x` cover contract.
 *
 * It lives apart from the repository so the rule can be unit tested without an Android
 * environment, and so the browser's "only 1.x slideshows" library filter and the thumbnail
 * pipeline can never drift from one another.
 */
object CoverRule {

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    /** Priority order for an exact basename match, best first. */
    private val EXACT_PRIORITY = listOf("1.jpg", "1.jpeg", "1.png", "1.webp", "1.gif")

    /**
     * @param entry the archive entry chosen as the cover, or null when the archive has no images.
     * @param strict true when the entry satisfied the `1.x` rule rather than a natural-sort fallback.
     */
    data class Match(val entry: String?, val strict: Boolean)

    fun isImageEntry(name: String): Boolean =
        !name.startsWith("__MACOSX/") &&
            !name.substringAfterLast('/').startsWith('.') &&
            name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS

    /** True when this archive belongs in a slideshow library at all. */
    fun isSlideshowArchive(imageEntries: List<String>): Boolean =
        imageEntries.any(::isImageEntry) && resolve(imageEntries).strict

    fun resolve(imageEntries: List<String>): Match {
        val candidates = imageEntries.filter(::isImageEntry)
        if (candidates.isEmpty()) return Match(null, false)

        // Shallowest path first, then natural order, so `1.jpg` at the root beats `sub/1.jpg`.
        val shallowestNatural = Comparator<String> { first, second ->
            val depth = first.count { it == '/' }.compareTo(second.count { it == '/' })
            if (depth != 0) depth else NaturalOrderComparator.compare(first, second)
        }

        for (exact in EXACT_PRIORITY) {
            candidates
                .filter { it.substringAfterLast('/').equals(exact, ignoreCase = true) }
                .minWithOrNull(shallowestNatural)
                ?.let { return Match(it, true) }
        }

        // Any other image whose basename starts with "1." (1.bmp, 1.heic, 1_title is not a match).
        candidates
            .filter { it.substringAfterLast('/').startsWith("1.", ignoreCase = true) }
            .minWithOrNull(shallowestNatural)
            ?.let { return Match(it, true) }

        return Match(candidates.minWithOrNull(NaturalOrderComparator), false)
    }
}
