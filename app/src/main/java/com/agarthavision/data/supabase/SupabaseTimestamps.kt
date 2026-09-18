package com.agarthavision.data.supabase

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Parses a `timestamptz` as PostgREST actually serialises it.
 *
 * `Instant.parse` is `DateTimeFormatter.ISO_INSTANT`, which requires the zone to be the literal
 * `Z`. Postgres renders an offset instead - `2026-09-18T06:19:18.22+00:00` - and varies the
 * fractional digits with the stored precision (`.22`, `.046`, `.425`). Every pull therefore died
 * with `DateTimeParseException ... at index 22`, and because `FetchRemoteDataUseCase` catches per
 * type, all four types failed while the sync still reported success.
 *
 * It stayed hidden until the server had its first row to return: with an empty remote there was
 * nothing to parse. A second medtech signing in, or any reinstall, would have got no data at all.
 *
 * `ISO_OFFSET_DATE_TIME` accepts both spellings and any fractional precision, so this also keeps
 * working if a column is ever stored at a different precision.
 */
internal fun parseSupabaseInstant(text: String): Instant = OffsetDateTime.parse(text).toInstant()
