package com.agarthavision.data.supabase

import com.agarthavision.core.util.Logger

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Decodes a list of raw PostgREST JSON elements into domain/data entities of type [T],
 * dropping and logging any row that fails deserialization or entity mapping.
 */
internal inline fun <reified R, T> List<JsonElement>.decodeRowsSkippingMalformed(
    json: Json,
    tag: String,
    rowKind: String,
    toEntity: (R) -> T,
): List<T> = mapNotNull { element ->
    runCatching {
        toEntity(json.decodeFromJsonElement<R>(element))
    }.onFailure { error ->
        Logger.w(tag, "Skipping malformed $rowKind row: ${error.message}")
    }.getOrNull()
}