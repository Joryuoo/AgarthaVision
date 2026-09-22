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

    /**
     * Masks an individual word according to GCash rules.
     */
    fun maskWord(word: String): String {
        val w = word.trim()
        val isInitial = w.endsWith('.') && w.length <= 2
        if (w.length <= 1 || isInitial) return w

        return when {
            w.contains('-') -> w.split('-').joinToString("-") { maskWord(it) }
            w.length == 2 -> "${w[0]}*"
            else -> "${w.first()}${"*".repeat(w.length - 2)}${w.last()}"
        }
    }
}
