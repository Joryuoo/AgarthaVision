package com.agarthavision.domain.model

/** The name and optional PNG to use while offline. */
class LaboratoryBranding(
    val name: String,
    val logoPng: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is LaboratoryBranding && name == other.name && logoPng.contentEquals(other.logoPng)

    override fun hashCode(): Int = 31 * name.hashCode() + logoPng.contentHashCode()
}
