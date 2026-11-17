package com.agarthavision.data.supabase

import com.agarthavision.domain.model.LaboratoryBranding
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.nio.ByteBuffer
import java.util.Base64
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reads only the caller's active laboratory. The RPC derives its scope from the session. */
class LaboratoryBrandingRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    suspend fun fetch(): LaboratoryBranding? {
        val rows = supabaseProvider.get().postgrest.rpc("get_my_laboratory_branding")
            .decodeList<BrandingRow>()
        check(rows.size <= 1) { "Expected at most one active laboratory" }
        return rows.singleOrNull()?.let { row ->
            require(row.name.isNotBlank()) { "Laboratory name is missing" }
            LaboratoryBranding(row.name, decodeLaboratoryLogo(row.logoBase64))
        }
    }

    @Serializable
    private data class BrandingRow(
        val name: String,
        @SerialName("logo_base64") val logoBase64: String? = null,
    )
}

private const val MAX_LOGO_BYTES = 131_072
private const val MAX_ENCODED_LOGO_LENGTH = ((MAX_LOGO_BYTES + 2) / 3) * 4
private const val MAX_LOGO_DIMENSION = 512
private const val MIN_PNG_BYTES = 57
private const val PNG_HEADER_BYTES = 24
private const val PNG_IHDR_LENGTH = 13
private const val PNG_IHDR_TYPE = 0x49484452
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
private val PNG_IEND = byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte())

internal fun decodeLaboratoryLogo(value: String?): ByteArray? {
    val bytes = value?.takeIf { it.length <= MAX_ENCODED_LOGO_LENGTH }
        ?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() }
        ?: return null
    return bytes.takeIf {
        it.size in MIN_PNG_BYTES..MAX_LOGO_BYTES && it.hasPngSignatureAndTrailer() && it.hasSupportedPngHeader()
    }
}

private fun ByteArray.hasPngSignatureAndTrailer(): Boolean =
    copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE) &&
        copyOfRange(size - PNG_IEND.size, size).contentEquals(PNG_IEND)

private fun ByteArray.hasSupportedPngHeader(): Boolean {
    val header = ByteBuffer.wrap(this, PNG_SIGNATURE.size, PNG_HEADER_BYTES - PNG_SIGNATURE.size)
    if (header.int != PNG_IHDR_LENGTH || header.int != PNG_IHDR_TYPE) return false
    val width = header.int
    val height = header.int
    return width in 1..MAX_LOGO_DIMENSION && height in 1..MAX_LOGO_DIMENSION
}
