package com.agarthavision.core.util

import com.agarthavision.domain.patient.CodenameGenerator

/**
 * GCash-style masking for patient names to protect privacy.
 *
 * Rules:
 * - First character and last character remain visible.
 * - All middle characters are replaced with '*'.
 * - Preserves single-letter initials like "M." or "M".
 * - Skips masking if the patient is codenamed (e.g. "M24-001").
 */
object NameMasking {

    /**
     * Masks [name] according to GCash conventions, preserving codenames and initials.
     */
    fun maskName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || CodenameGenerator.isCodename(trimmed)) {
            return name
        }

        return when {
            trimmed.contains(',') -> {
                val parts = trimmed.split(',', limit = 2)
                val maskedLast = maskWord(parts[0].trim())
                val rest = parts[1].trim()
                val maskedRest = rest.split(Regex("""\s+""")).joinToString(" ") { maskWord(it) }
                "$maskedLast, $maskedRest"
            }
            trimmed.contains(' ') -> {
                trimmed.split(Regex("""\s+""")).joinToString(" ") { maskWord(it) }
            }
            else -> maskWord(trimmed)
        }
    }

    private const val BULLET = "•"
    private const val SHORT_NAME_LENGTH = 2
    private const val MEDIUM_NAME_LENGTH = 3

    /**
     * Masks an individual word according to formal masked rules:
     * - Preserves single letters and initials (e.g. "M.")
     * - 2 letters: first letter + 1 bullet (e.g. "Li" -> "L•")
     * - 3 letters: first letter + 1 bullet + last letter (e.g. "Ben" -> "B•N")
     * - 4+ letters: first letter + 2 bullets + last letter (e.g. "Escolano" -> "E••O", "Joseph" -> "J••P")
     * Formatted in uppercase for formality.
     */
    fun maskWord(word: String): String {
        val w = word.trim().uppercase()
        val isInitial = w.endsWith('.') && w.length <= SHORT_NAME_LENGTH
        if (w.length <= 1 || isInitial) return w

        return when {
            w.contains('-') -> w.split('-').joinToString("-") { maskWord(it) }
            w.length == SHORT_NAME_LENGTH -> "${w.first()}$BULLET"
            w.length == MEDIUM_NAME_LENGTH -> "${w.first()}$BULLET${w.last()}"
            else -> "${w.first()}$BULLET$BULLET${w.last()}"
        }
    }
}
