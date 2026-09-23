package com.agarthavision.ui.verify

/**
 * Stable handles for the verification sheets' UI tests.
 *
 * Tags rather than display text: the sheets' labels are product copy that changes
 * without any behaviour changing, and tests keyed on copy would fail for the wrong
 * reason. Anything here is a contract with `src/test/java/.../ui/verify/` — renaming
 * a tag means updating the tests in the same change.
 */
internal object VerifyTestTags {
    /** [SheetActionRow]'s confirm button — "Submit" at both sheet call sites. */
    const val SHEET_PRIMARY_ACTION = "sheet_primary_action"

    /** [SheetActionRow]'s destructive button — "Discard" at both sheet call sites. */
    const val SHEET_SECONDARY_ACTION = "sheet_secondary_action"

    /** Frame-level queue navigation. */
    const val FRAME_PREV = "frame_prev"
    const val FRAME_NEXT = "frame_next"

    /** The JPEG preview. Present whether or not the image itself decodes. */
    const val FRAME_PREVIEW = "frame_preview"

    /**
     * Shown in the preview's place when there is no image to load at all — neither local bytes,
     * nor a local file, nor a signed Storage URL.
     */
    const val FRAME_UNAVAILABLE = "frame_unavailable"

    /** Detection-level navigation within a frame (AI sheet only). */
    const val DETECTION_PREV = "detection_prev"
    const val DETECTION_NEXT = "detection_next"

    /** Free-text observation field on both sheets. */
    const val NOTE_FIELD = "note_field"

    /** Quick-pick species chip. Suffixed with the [com.agarthavision.domain.model.EggSpecies] name. */
    const val SPECIES_CHIP_PREFIX = "species_chip_"

    /** The "Other..." chip that opens the custom-species dialog. */
    const val SPECIES_CHIP_OTHER = "species_chip_other"

    /**
     * Discard-confirmation dialog. Its confirm button reads "Discard", the same copy as
     * [SHEET_SECONDARY_ACTION] that opens it, so these two must be told apart by tag.
     */
    const val DISCARD_DIALOG_CONFIRM = "discard_dialog_confirm"
    const val DISCARD_DIALOG_DISMISS = "discard_dialog_dismiss"

    /** Custom-species dialog opened by [SPECIES_CHIP_OTHER]. */
    const val CUSTOM_SPECIES_FIELD = "custom_species_field"
    const val CUSTOM_SPECIES_SAVE = "custom_species_save"
    const val CUSTOM_SPECIES_DISMISS = "custom_species_dismiss"

    /**
     * The AI sheet's three checkboxes. They carry statements rather than questions now, but the
     * tags keep the Q names: they are what the constraint docs and the tickets call them, and
     * the chain's order is still Q1 gates Q2 gates Q3.
     */
    const val QUESTION_Q1 = "q1"
    const val QUESTION_Q2 = "q2"

    /** "This egg is <suggested species>" — shown only when the model's class is a known species. */
    const val QUESTION_Q3 = "q3"

    // There is no QUESTION_Q4. "Did the model miss any eggs in this frame?" is derived from the
    // findings rather than asked, so there is no control to tag.

    /**
     * Species picker, shown once Q1 and Q2 are both yes and either the medtech said the
     * suggested species is wrong or the model's class is not a known species.
     */
    const val SPECIES_DROPDOWN = "species_dropdown"


    /** Bounding-box visibility switch. */
    const val BOXES_TOGGLE = "boxes_toggle"

    /** The overlay the boxes are drawn on, and the surface a new box is dragged out on. */
    const val FRAME_CANVAS = "frame_canvas"

    /** Accept and cancel for a box being drawn. */
    const val DRAW_ACCEPT = "draw_accept"
    const val DRAW_CANCEL = "draw_cancel"

    /** "Redraw the box", offered once Q2 is answered "No" and before a box has been replaced. */
    const val REDRAW_BOX = "redraw_box"

    /** The line saying a box has been replaced, which is also why Q2 is latched at "No". */
    const val BOX_REPLACED_NOTE = "box_replaced_note"

    /**
     * Caution line under the model-output summary, present only when the model named something.
     *
     * It used to sit under a maroon card naming the current detection's species, once per box.
     * The card went with the section restructure - the species it announced is asked about
     * directly by Q3, and the source it badged is now the whole point of the model-output
     * section's three states.
     */
    const val AI_SUGGESTION_NOTE = "ai_suggestion_note"

    /** The model-output section. Present for every frame; its body is one of three states. */
    const val MODEL_OUTPUT_PANEL = "model_output_panel"

    /** The spinner inside the model-output section while inference has not come back. */
    const val MODEL_OUTPUT_SPINNER = "model_output_spinner"

    /** "Add species" button beneath the added-species cards. Present on every frame. */
    const val ADD_SPECIES = "add_species"

    /**
     * The disclosure that reveals the added eggs still waiting for a box.
     *
     * Present only when at least one added egg has no box. Its label carries "n of m located",
     * which is the whole reason it is a disclosure and not a hidden screen.
     */
    const val LOCATE_TOGGLE = "locate_toggle"


    /**
     * The species already on this device, offered under whichever "Other species" field is
     * being typed into.
     *
     * One tag, not one per field: only one free-text field is ever being typed into, and the
     * view model's own [VerificationUiState.suggestionsFor] is what keeps a list from rendering
     * under a field it does not belong to. A test asserting there is exactly one of these is
     * asserting that rule.
     */
    const val OTHER_SPECIES_SUGGESTIONS = "other_species_suggestions"

    // The manual-capture checklist tags went with the checklist. A frame captured while the
    // inference container was unreachable is verified through the same Add Egg section as every
    // other frame, so there is no separate set of controls to address.

    fun speciesChip(speciesName: String): String = SPECIES_CHIP_PREFIX + speciesName

    /** One offered species name, keyed on the name so a test can tap the one it means. */
    fun otherSpeciesSuggestion(speciesName: String): String =
        "other_species_suggestion_" + speciesName

    /** Remove button on the added finding at [index]. */
    fun removeFinding(index: Int): String = "remove_finding_" + index

    /** Field-total field on the added species at [index]. */
    fun countField(index: Int): String = "count_field_" + index

    /** Species picker on the added finding at [index]. */
    fun addedSpeciesDropdown(index: Int): String = "added_species_dropdown_" + index

    fun addedSpeciesSummary(index: Int): String = "added_species_summary_" + index
    fun addedSpeciesForm(index: Int): String = "added_species_form_" + index

    /**
     * Draw / redraw affordance for egg [slot] of the added species at [findingIndex].
     *
     * Keyed on both, because an added species is one row holding several eggs now, and a tag
     * naming only the row would collide across every egg under it.
     */
    fun drawBox(findingIndex: Int, slot: Int): String = "draw_box_" + findingIndex + "_" + slot

    // There is no questionOption. The three questions are checkboxes rather than Yes/No pairs,
    // so a question has one control and its own tag is enough to reach it.
}
