package com.agarthavision.ui.image

import coil.key.Keyer
import coil.request.Options
import java.io.File

class SampleImageKeyer : Keyer<SampleImageRef> {
    override fun key(data: SampleImageRef, options: Options): String? =
        data.localPath?.takeIf { it.isNotBlank() && File(it).isFile }
            ?: data.storagePath?.takeIf { it.isNotBlank() }
}
