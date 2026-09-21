package com.agarthavision.ui.patients

import com.agarthavision.ui.sessions.limitInput

/**
 * The maximum length of a patient name field, sized for one line of the list row's title.
 *
 * Single source of truth shared by [PatientFormViewModel] and [PatientFormScreen]. The
 * private `NAME_MAX_LENGTH` constant that used to live only in the VM is replaced by this
 * so the screen can pass the same bound without importing VM internals.
 */
internal const val PATIENT_NAME_MAX_LENGTH = 40

/**
 * Strips characters not expected in a Philippine personal name.
 *
 * Keeps: Unicode letters (covers Latin diacritics and other scripts that appear in
 * multi-heritage names), spaces, hyphens, apostrophes, and periods. Everything else —
 * digits, emoji, and other punctuation — is discarded. Emoji are handled codepoint-by-
 * codepoint so surrogate-pair emoji are removed entirely rather than left as broken halves.
 */
@Suppress("ComplexCondition") // Five terms, each a single predicate; extracting into a named set
// would be less readable than the inline OR chain for a one-off character allowlist.
internal fun sanitizeName(input: String): String = buildString {
    var i = 0
    while (i < input.length) {
        val cp = input.codePointAt(i)
        if (Character.isLetter(cp) ||
            cp == ' '.code ||
            cp == '-'.code ||
            cp == '\''.code ||
            cp == '.'.code
        ) {
            appendCodePoint(cp)
        }
        i += Character.charCount(cp)
    }
}

/**
 * Uppercases only the first character of [input], leaving every subsequent character
 * unchanged.
 *
 * **Not** title-case — "de la Cruz" becomes "De la Cruz", not "De La Cruz".  Uses
 * codepoint-aware extraction at position 0 so a supplementary-plane letter does not leave
 * a broken surrogate in the result.
 */
internal fun capitalizeFirst(input: String): String {
    if (input.isEmpty()) return input
    val cp = input.codePointAt(0)
    val upper = Character.toUpperCase(cp)
    return if (upper == cp) input
    else String(Character.toChars(upper)) + input.substring(Character.charCount(cp))
}

/**
 * Applies the canonical name-field transform pipeline in order:
 * 1. [sanitizeName] — strip characters not expected in a Philippine name
 * 2. [capitalizeFirst] — uppercase the first character only
 * 3. [limitInput] — enforce [PATIENT_NAME_MAX_LENGTH], preserving the unchanged prefix and
 *    suffix so mid-string edits do not silently drop characters the user did not touch
 */
internal fun transformNameInput(previous: String, proposed: String): String =
    limitInput(previous, sanitizeName(proposed).let(::capitalizeFirst), PATIENT_NAME_MAX_LENGTH)
