package com.agarthavision.domain.usecase.reports

import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.LpfDensity

/**
 * The one definition of a session's per-species LPF range (PB-17).
 *
 * **One, deliberately.** This lived twice — once in `SessionEggCountUseCase` for the screen and
 * once, copied, in `GenerateSessionReportUseCase` for the report — so the screen and the
 * document a medtech hands a patient could disagree about the same session and neither would
 * be obviously wrong. PB-17 warns about two live definitions of this figure in two *tickets*;
 * two in two *files* is the same failure already committed. Anything computing this calls here.
 *
 * ### The rules, each of which is a distinct way to get it wrong
 *
 * 1. **A field containing none of that species contributes 0, not absence.** If Ascaris appears
 *    in 3 of 10 fields the minimum is 0, not the smallest of the three counts. Filtering the
 *    empty fields out would overstate every result on every report.
 * 2. **The denominator is whatever the medtech actually recorded.** No floor, no cap, no
 *    warning at 9 fields or at 14. Ten is typical practice, not a rule the app enforces — it is
 *    the medtech's professional judgement, and a medtech who reads more or fewer needs no code
 *    change to be served correctly.
 * 3. **A wholly negative session is a real result**, and the most common one in surveillance.
 *    Ten clean fields produce no species rows at all, which callers must render as "no
 *    parasites found" and still generate a report for — not as an empty state, and never as a
 *    reason to produce nothing.
 *
 * Zero-egg fields only exist as data because 86d4a6prb made a zero-detection inference persist
 * a `FrameSource.MODEL` frame with an empty predictions list rather than discarding it. That
 * behaviour is load-bearing for rule 1 — if it regresses, every minimum silently rises.
 *
 * @param fieldCount fields examined in this session. Rule 1 reads it: a species with rows in
 *   fewer fields than this was absent from the rest.
 */
fun aggregateLpfPerSpecies(
    findings: List<SampleSpeciesFindingEntity>,
    fieldCount: Int,
): Map<String, LpfDensity> =
    findings.groupBy { it.species }.mapValues { (_, speciesFindings) ->
        // A species can hold more than one row in one field; the field's count is their sum.
        val countsByField = speciesFindings
            .groupBy { it.sampleId }
            .mapValues { (_, rows) -> rows.sumOf { it.eggCount } }

        // Guarded rather than trusted: a findings set covering more fields than the caller
        // counted means the two disagree, and taking the larger is the reading that cannot
        // invent a non-zero minimum out of a miscount.
        val fields = maxOf(fieldCount, countsByField.size)

        LpfDensity(
            min = if (countsByField.size < fields) 0 else countsByField.values.min(),
            max = countsByField.values.maxOrNull() ?: 0,
        )
    }
