package com.example

import com.example.util.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testNaturalOrderComparator_numericSequence() {
        val list = listOf("10.jpg", "2.jpg", "1.jpg", "20.jpg", "3.jpg")
        val sorted = list.sortedWith(NaturalOrderComparator)
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg", "10.jpg", "20.jpg"), sorted)
    }

    @Test
    fun testNaturalOrderComparator_prefixAndPad() {
        val list = listOf("frame_10.png", "frame_2.png", "frame_01.png")
        val sorted = list.sortedWith(NaturalOrderComparator)
        assertEquals(listOf("frame_01.png", "frame_2.png", "frame_10.png"), sorted)
    }

    @Test
    fun testCoverResolutionOrder() {
        // Test 1: Exactly 1.jpg preferred over others
        val entries1 = listOf("photos/cover.jpg", "1.jpg", "2.jpg", "10.jpg")
        val exact1 = entries1.filter { it.substringAfterLast('/').equals("1.jpg", ignoreCase = true) }
        assertEquals(1, exact1.size)
        assertEquals("1.jpg", exact1.first())

        // Test 2: Shallowest 1.jpg preferred when present at multiple depths
        val entries2 = listOf("folder/sub/1.jpg", "1.jpg", "folder/1.jpg")
        val shallowest = entries2.minByOrNull { it.count { c -> c == '/' } }
        assertEquals("1.jpg", shallowest)

        // Test 3: Natural sort fallback when 1.jpg is missing
        val entries3 = listOf("10.jpg", "2.jpg", "3.jpg")
        val sortedFallback = entries3.sortedWith(NaturalOrderComparator)
        assertEquals("2.jpg", sortedFallback.first())
    }
}
