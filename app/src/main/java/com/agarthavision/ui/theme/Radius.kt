package com.agarthavision.ui.theme

import androidx.compose.ui.unit.dp
import com.komoui.themes.KomoRadius

// docs/components.md §4 — generous rounding anchored at lg = 12.dp
// Never introduce new radii outside this scale.
object AgarthaRadius : KomoRadius {
    override val radius = 12.dp  // base / lg anchor
    override val sm     =  8.dp  // chips, mini tags
    override val md     = 10.dp  // inputs, popovers
    override val lg     = 12.dp  // most controls
    override val xl     = 16.dp  // cards, dialogs
    override val xxl    = 20.dp  // extra-large panels
    override val xl3    = 24.dp  // maximum non-pill surface
    override val full   = 999.dp // pill buttons (Button default + secondary variants)
}
