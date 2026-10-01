package com.example.util

import java.math.BigInteger
import java.util.regex.Pattern

/**
 * Natural sort comparator: sorts strings taking numeric sequences into account.
 * For example: "2.jpg" comes before "10.jpg", "frame_1.png" before "frame_02.png".
 * Used consistently across the entire app for:
 * 1. Grid sorting by filename
 * 2. Zip entry sorting inside each zip file
 * 3. Fallback resolution for the first image in a zip
 */
object NaturalOrderComparator : Comparator<String> {

    private val CHUNK_PATTERN = Pattern.compile("(\\d+|\\D+)")

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val matcher1 = CHUNK_PATTERN.matcher(s1)
        val matcher2 = CHUNK_PATTERN.matcher(s2)

        while (matcher1.find()) {
            if (!matcher2.find()) {
                return 1 // s1 has more chunks
            }

            val chunk1 = matcher1.group(1) ?: ""
            val chunk2 = matcher2.group(1) ?: ""

            val isDigit1 = chunk1.isNotEmpty() && chunk1[0].isDigit()
            val isDigit2 = chunk2.isNotEmpty() && chunk2[0].isDigit()

            if (isDigit1 && isDigit2) {
                // Compare numerically
                val num1 = chunk1.toBigIntegerOrNull() ?: BigInteger.ZERO
                val num2 = chunk2.toBigIntegerOrNull() ?: BigInteger.ZERO
                val numComparison = num1.compareTo(num2)
                if (numComparison != 0) {
                    return numComparison
                }
                // If numbers are equal (e.g. "01" vs "1"), shorter chunk or string comparison
                val lenDiff = chunk1.length - chunk2.length
                if (lenDiff != 0) {
                    return lenDiff
                }
            } else {
                val textComparison = chunk1.compareTo(chunk2, ignoreCase = true)
                if (textComparison != 0) {
                    return textComparison
                }
            }
        }

        return if (matcher2.find()) -1 else s1.compareTo(s2)
    }
}
