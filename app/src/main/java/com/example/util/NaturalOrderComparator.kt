package com.example.util

/**
 * One natural-sort key used for archive cards, zip entries, and cover fallback selection.
 * Padding makes lexical sort deterministic while keeping the implementation allocation-light.
 */
private val DIGITS = Regex("(\\d+)")

fun naturalKey(value: String): String =
    value.lowercase().replace(DIGITS) { match -> match.value.padStart(10, '0') }

object NaturalOrderComparator : Comparator<String> {
    override fun compare(first: String?, second: String?): Int = when {
        first === second -> 0
        first == null -> -1
        second == null -> 1
        else -> naturalKey(first).compareTo(naturalKey(second)).takeIf { it != 0 }
            ?: first.compareTo(second, ignoreCase = true)
    }
}
