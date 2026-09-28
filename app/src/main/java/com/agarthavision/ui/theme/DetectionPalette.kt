package com.agarthavision.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colours the Sample Data Screen gives its detection boxes.
 *
 * **No two boxes on a frame share a colour**, so a medtech reading a crowded field can tell which
 * toggle belongs to which box. "No two" needs a bound, so this is a **fixed palette of eight that
 * cycles** past the eighth. That is a decision, not an oversight: more than eight detections in
 * one low-power field is vanishingly rare, and a cycle is a better failure mode than an unbounded
 * generated palette that eventually emits two colours nobody can tell apart.
 *
 * **This differs from the Verification Screen's rule** (current detection coloured, every other
 * box gray — see `VerificationBoxColors`). Two screens, two rules, both deliberate: that screen
 * focuses on one detection at a time and wants the others to recede, this one shows them all at
 * once and has to distinguish them. Do not "fix" the inconsistency.
 *
 * **Mode-independent, unlike every other colour in this file's neighbourhood.** These are drawn
 * over a photograph of a stained smear, not over an app surface, and the smear's brightness does
 * not change with the app's theme. A palette that lightened in dark mode would be answering the
 * wrong question.
 *
 * The values are the Okabe–Ito qualitative set, which stays distinguishable under the common
 * colour-vision deficiencies — worth having on a screen where the colour *is* the label. Its
 * eighth entry is black, replaced here with white, because black recedes into a dark field. This
 * is the one place blue appears in the app: the retired cobalt palette was a **brand** decision
 * about UI chrome, and these are categorical marks on an image, not chrome.
 */
val DetectionBoxPalette: List<Color> = listOf(
    Color(0xFFE69F00), // orange
    Color(0xFF56B4E9), // sky blue
    Color(0xFF009E73), // bluish green
    Color(0xFFF0E442), // yellow
    Color(0xFF0072B2), // blue
    Color(0xFFD55E00), // vermillion
    Color(0xFFCC79A7), // reddish purple
    Color(0xFFFFFFFF), // white
)

/**
 * The colour for the detection at [index], cycling past the end of [DetectionBoxPalette].
 *
 * Cycling rather than clamping: two boxes sharing a colour is bad, but every box past the eighth
 * sharing one colour is worse.
 */
fun detectionBoxColor(index: Int): Color =
    DetectionBoxPalette[Math.floorMod(index, DetectionBoxPalette.size)]
