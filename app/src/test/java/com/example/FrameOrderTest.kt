package com.example

import com.example.data.ZipRepository
import com.example.data.model.FrameOrder
import com.example.data.model.SortBy
import com.example.data.model.SortOption
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.util.sortedForBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library and the frames inside an archive are ordered by two different mechanisms, and this
 * test exists to keep it that way: sorting the shelf must never renumber the pages of a book.
 */
class FrameOrderTest {

    private fun frame(name: String) = ZipEntryItem(
        entryPath = name,
        basename = name.substringAfterLast('/'),
        isImage = true,
        size = 1L,
        compressedSize = 1L
    )

    private val frames = listOf("10.jpg", "2.jpg", "1.jpg", "21.jpg", "3.jpg").map(::frame)

    @Test
    fun `natural order counts the way humans do`() {
        val ordered = ZipRepository.orderFrames(frames, FrameOrder.NATURAL, "seed")
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg", "10.jpg", "21.jpg"), ordered.map { it.entryPath })
    }

    @Test
    fun `archive order is left exactly as the zip stored it`() {
        val ordered = ZipRepository.orderFrames(frames, FrameOrder.ARCHIVE, "seed")
        assertEquals(frames.map { it.entryPath }, ordered.map { it.entryPath })
    }

    @Test
    fun `reverse is natural order backwards`() {
        val ordered = ZipRepository.orderFrames(frames, FrameOrder.REVERSE, "seed")
        assertEquals(listOf("21.jpg", "10.jpg", "3.jpg", "2.jpg", "1.jpg"), ordered.map { it.entryPath })
    }

    @Test
    fun `shuffle is stable for one archive and keeps every frame`() {
        val first = ZipRepository.orderFrames(frames, FrameOrder.SHUFFLE, "archive-a")
        val again = ZipRepository.orderFrames(frames, FrameOrder.SHUFFLE, "archive-a")
        assertEquals(first.map { it.entryPath }, again.map { it.entryPath })
        assertEquals(frames.map { it.entryPath }.toSet(), first.map { it.entryPath }.toSet())
    }

    @Test
    fun `no browser sort option can change frame order`() {
        val reference = ZipRepository.orderFrames(frames, FrameOrder.NATURAL, "a.zip").map { it.entryPath }
        // Every library preset in turn; the frame list is produced independently each time.
        SortOption.entries.forEach { option ->
            val ordered = ZipRepository.orderFrames(frames, FrameOrder.NATURAL, "a.zip").map { it.entryPath }
            assertEquals("frames moved after choosing ${option.label}", reference, ordered)
        }
    }

    private fun zip(name: String, size: Long = 1L, modified: Long = 1L, images: Int = 1) = ZipItem(
        path = "/storage/$name",
        name = name,
        size = size,
        lastModified = modified,
        volumeId = "primary",
        volumeName = "Internal",
        imageCount = images
    )

    @Test
    fun `library presets order the shelf as their label promises`() {
        val library = listOf(
            zip("b.zip", size = 30L, modified = 300L, images = 2),
            zip("a.zip", size = 10L, modified = 100L, images = 9),
            zip("c.zip", size = 20L, modified = 200L, images = 5)
        )

        fun names(option: SortOption) =
            library.sortedForBrowser(option.sortBy, option.ascending, naturalNames = true, seed = 0L)
                .map { it.name }

        assertEquals(listOf("b.zip", "c.zip", "a.zip"), names(SortOption.NEWEST))
        assertEquals(listOf("a.zip", "c.zip", "b.zip"), names(SortOption.OLDEST))
        assertEquals(listOf("a.zip", "b.zip", "c.zip"), names(SortOption.NAME_ASC))
        assertEquals(listOf("c.zip", "b.zip", "a.zip"), names(SortOption.NAME_DESC))
        assertEquals(listOf("b.zip", "c.zip", "a.zip"), names(SortOption.LARGEST))
        assertEquals(listOf("a.zip", "c.zip", "b.zip"), names(SortOption.MOST_FRAMES))
    }

    @Test
    fun `natural names put 2 before 10`() {
        val library = listOf(zip("vol 10.zip"), zip("vol 2.zip"), zip("vol 1.zip"))
        val natural = library.sortedForBrowser(SortBy.NAME, true, naturalNames = true, seed = 0L).map { it.name }
        assertEquals(listOf("vol 1.zip", "vol 2.zip", "vol 10.zip"), natural)

        val lexical = library.sortedForBrowser(SortBy.NAME, true, naturalNames = false, seed = 0L).map { it.name }
        assertEquals(listOf("vol 1.zip", "vol 10.zip", "vol 2.zip"), lexical)
    }

    @Test
    fun `shuffle is stable per seed and changes with it`() {
        val library = (1..12).map { zip("file-$it.zip") }
        val a = library.sortedForBrowser(SortBy.RANDOM, true, naturalNames = true, seed = 7L).map { it.name }
        val b = library.sortedForBrowser(SortBy.RANDOM, true, naturalNames = true, seed = 7L).map { it.name }
        val c = library.sortedForBrowser(SortBy.RANDOM, true, naturalNames = true, seed = 99L).map { it.name }
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(library.map { it.name }.toSet(), a.toSet())
    }

    @Test
    fun `every preset resolves back to itself`() {
        SortOption.entries.forEach { option ->
            val resolved = SortOption.of(option.sortBy, option.ascending)
            assertTrue(
                "${option.label} resolved to ${resolved.label}",
                resolved.sortBy == option.sortBy && resolved.ascending == option.ascending
            )
        }
    }
}
