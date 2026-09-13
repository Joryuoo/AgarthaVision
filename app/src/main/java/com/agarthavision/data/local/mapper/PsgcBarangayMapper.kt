package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.PsgcBarangayEntity
import com.agarthavision.domain.model.PsgcBarangay

/**
 * Converts a Room PSGC row into the domain model used by the barangay picker.
 *
 * The search haystack and the parent codes stay in the data layer — the picker needs the
 * code and the names, nothing else.
 */
fun PsgcBarangayEntity.toDomain(): PsgcBarangay =
    PsgcBarangay(
        code = code,
        name = name,
        cityMuniName = cityMuniName,
        provinceName = provinceName,
        regionName = regionName,
    )
