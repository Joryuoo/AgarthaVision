package com.agarthavision.ui.sessions

/**
 * Longest session label the New Session sheet accepts.
 *
 * **32, not 20.** The generated label `C.G.-0730600000-001` is exactly 20 characters, so at
 * the old cap it fitted with no room at all and the first character a medtech typed while
 * editing was silently dropped — the field looked like it had simply ignored the keystroke.
 * 32 leaves room to edit, and room for a sequence that has run past three digits.
 *
 * `SESSION_NOTE_MAX_LENGTH` is gone with the note itself (PB-09).
 */
internal const val SESSION_LABEL_MAX_LENGTH = 32

/**
 * Bounds a text-field edit to [maxLength] the way Android's `LengthFilter` does: the text the
 * user already had is kept intact and only the newly inserted piece is cut to fit. A plain
 * `take(maxLength)` would instead drop characters off the tail when typing or pasting into
 * the middle of a full field, silently mutating text the user did not touch.
 */
internal fun limitInput(previous: String, proposed: String, maxLength: Int): String {
    if (proposed.length <= maxLength) return proposed
    val prefix = previous.commonPrefixWith(proposed).length
    val suffix = previous.commonSuffixWith(proposed).length
        .coerceAtMost(minOf(previous.length, proposed.length) - prefix)
    val room = maxLength - prefix - suffix
    return if (room <= 0) {
        previous
    } else {
        val inserted = proposed.substring(prefix, proposed.length - suffix)
        proposed.substring(0, prefix) + inserted.take(room) + proposed.substring(proposed.length - suffix)
    }
}
