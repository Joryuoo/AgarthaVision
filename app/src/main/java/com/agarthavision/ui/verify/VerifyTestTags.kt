package com.agarthavision.ui.verify

import com.agarthavision.domain.model.EggSpecies

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
     * The AI sheet's three yes/no questions. All three render the same "Yes"/"No" labels,
     * so a test that looked them up by text would match whichever came first.
     */
    const val QUESTION_Q1 = "q1"
    const val QUESTION_Q2 = "q2"

    /** "Is this egg <suggested species>?" — shown only when the model's class is a known species. */
    const val QUESTION_Q3 = "q3"
    const val QUESTION_Q4 = "q4"

    /**
     * Species picker, shown once Q1 and Q2 are both yes and either the medtech said the
     * suggested species is wrong or the model's class is not a known species.
     */
    const val SPECIES_DROPDOWN = "species_dropdown"


    /** Bounding-box visibility switch. */
    const val BOXES_TOGGLE = "boxes_toggle"

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

    /** "Add species" button beneath the added-findings list. */
    const val ADD_SPECIES = "add_species"

    /** The live per-species summary of what submitting would write. */
    const val FINDINGS_SUMMARY = "findings_summary"

    const val MANUAL_NO_DETECTION = "manual_no_detection"
    const val MANUAL_OTHER_NAME_FIELD = "manual_other_name_field"

    fun manualSpeciesCheckbox(species: EggSpecies): String = "manual_species_" + species.name
    fun manualCountField(species: EggSpecies): String = "manual_count_" + species.name

    fun speciesChip(speciesName: String): String = SPECIES_CHIP_PREFIX + speciesName

    /** Remove button on the added finding at [index]. */
    fun removeFinding(index: Int): String = "remove_finding_" + index

    /** Egg-count field on the added finding at [index]. */
    fun countField(index: Int): String = "count_field_" + index

    /** Species picker on the added finding at [index]. */
    fun addedSpeciesDropdown(index: Int): String = "added_species_dropdown_" + index

    /** One option within a question, e.g. `questionOption(QUESTION_Q1, "Yes")`. */
    fun questionOption(question: String, label: String): String =
        question + "_" + label.lowercase()
}
