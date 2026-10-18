package com.example

import com.example.util.CoverRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the production cover contract itself. The browser's "only 1.x slideshows" rule is
 * driven entirely by [CoverRule.Match.strict], so these cases double as the library filter spec.
 */
class CoverRuleTest {

    @Test
    fun exactOneJpgWinsOverEverythingElse() {
        val match = CoverRule.resolve(listOf("photos/cover.jpg", "1.jpg", "2.jpg", "10.jpg"))
        assertEquals("1.jpg", match.entry)
        assertTrue(match.strict)
    }

    @Test
    fun rootCoverBeatsNestedCover() {
        val match = CoverRule.resolve(listOf("folder/sub/1.jpg", "folder/1.jpg", "1.jpg"))
        assertEquals("1.jpg", match.entry)
    }

    @Test
    fun nestedCoverIsStillStrictWhenNoRootCoverExists() {
        val match = CoverRule.resolve(listOf("deep/sub/1.jpg", "deep/2.jpg"))
        assertEquals("deep/sub/1.jpg", match.entry)
        assertTrue(match.strict)
    }

    @Test
    fun extensionPriorityFollowsDocumentedOrder() {
        val match = CoverRule.resolve(listOf("1.gif", "1.webp", "1.png", "1.jpeg"))
        assertEquals("1.jpeg", match.entry)
    }

    @Test
    fun uncommonOneExtensionStillCounts() {
        val match = CoverRule.resolve(listOf("3.jpg", "1.heic", "2.jpg"))
        assertEquals("1.heic", match.entry)
        assertTrue(match.strict)
    }

    @Test
    fun naturalSortFallbackIsNotStrict() {
        val match = CoverRule.resolve(listOf("10.jpg", "2.jpg", "3.jpg"))
        assertEquals("2.jpg", match.entry)
        assertFalse(match.strict)
        assertFalse(CoverRule.isSlideshowArchive(listOf("10.jpg", "2.jpg", "3.jpg")))
    }

    @Test
    fun underscoreNamesAreNotCovers() {
        val match = CoverRule.resolve(listOf("1_title.jpg", "2.jpg"))
        assertEquals("1_title.jpg", match.entry)
        assertFalse(match.strict)
    }

    @Test
    fun macosxJunkAndDotFilesAreIgnored() {
        val entries = listOf("__MACOSX/1.jpg", ".hidden/1.jpg", "2.jpg", "1.jpg")
        val match = CoverRule.resolve(entries)
        assertEquals("1.jpg", match.entry)
        assertFalse(CoverRule.isImageEntry("__MACOSX/1.jpg"))
        assertFalse(CoverRule.isImageEntry("notes.txt"))
        assertTrue(CoverRule.isImageEntry("gallery/4.PNG"))
    }

    @Test
    fun archiveWithoutImagesHasNoCover() {
        val match = CoverRule.resolve(listOf("readme.txt", "data.json"))
        assertNull(match.entry)
        assertFalse(match.strict)
        assertFalse(CoverRule.isSlideshowArchive(listOf("readme.txt")))
    }

    @Test
    fun strictArchiveIsAcceptedByTheLibraryRule() {
        assertTrue(CoverRule.isSlideshowArchive(listOf("1.jpg", "2.jpg", "3.jpg")))
    }
}
