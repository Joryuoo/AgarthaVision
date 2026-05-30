package com.agarthavision.domain.model

enum class EggSpecies(
    val canonicalClass: String?,
    private val knownAliases: Set<String> = emptySet(),
) {
    ASCARIS("Ascaris lumbricoides", setOf("Ascaris")),
    TRICHURIS("Trichuris trichiura", setOf("Trichuris")),
    HOOKWORM("Hookworm"),
    OTHER(null),
    ;
    val displayName: String get() = canonicalClass ?: "Other"

    companion object {
        fun fromClassLabel(label: String): EggSpecies? {
            val trimmed = label.trim()
            return entries.firstOrNull { species ->
                val canonical = species.canonicalClass ?: return@firstOrNull false
                canonical.equals(trimmed, ignoreCase = true) ||
                    species.knownAliases.any { it.equals(trimmed, ignoreCase = true) }
            }
        }
    }
}
