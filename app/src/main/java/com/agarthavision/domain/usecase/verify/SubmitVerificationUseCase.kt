package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.data.local.mapper.toDetectionEntities
import com.agarthavision.data.local.mapper.toFindingEntity
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.sync.SyncScheduler
import java.time.Instant
import javax.inject.Inject

/**
 * Turns a reviewed frame into a verified sample.
 *
 * This is the only path that writes a verified sample, which is what makes constraint C7
 * structural rather than aspirational: no model output becomes a finding without a human
 * submission passing through here.
 *
 * **Idempotent per sample.** A verified sample stays editable (ticket 86d4ab4vm), so this runs
 * more than once on the same id. Every write below is a replace rather than an append — see the
 * deterministic ids in `VerificationMapper` — because appending would silently double every egg
 * count on the second save.
 */
@Suppress("LongParameterList")
class SubmitVerificationUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val findingDao: SampleSpeciesFindingDao,
    private val syncSampleUseCase: SyncSampleUseCase,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        frame: FlaggedFrame,
        findings: List<Finding>,
        missedEgg: Boolean?,
        userNote: String? = null,
    ): Result<String> = runCatching {
        // Per ADR-007, verification works offline. No auth is required — the sample keeps
        // its existing owner (cached identity or null) and is claimed at the next login.
        val sampleId = frame.sampleId
        require(sampleId.isNotBlank()) { "Flagged sample id is required." }
        val verifiedAt = Instant.now()

        // Setting status back to VERIFIED is what re-arms sync on an edit: an already-SYNCED
        // sample re-enters getSamplesPendingSync, so SyncPendingDataUseCase pushes the edit
        // when connectivity returns. Without it an offline edit would never reach Supabase.
        sampleDao.updateSampleOnVerify(
            sampleId = sampleId,
            status = SampleStatus.VERIFIED.value,
            verifiedAt = verifiedAt.toEpochMilli(),
            needsReannotation = missedEgg == true,
            userNote = userNote?.takeIf { it.isNotBlank() },
        )

        // A rejected box still persists, as a labelled FALSE_POSITIVE row — that is what makes
        // detections a retraining corpus instead of a results table (C8). So every finding is
        // written, not just the ones that counted.
        val detections = findings.toDetectionEntities(sampleId)
        detectionDao.insertDetections(detections)

        // An added species the medtech counted down on re-open — 23 Ascaris, then 20 — leaves
        // three slots behind, because the insert above replaces and never deletes. Left alone
        // they stay as null-bbox rows for eggs nobody claims any more, inflating the corpus and
        // making the frame read as un-localised forever.
        //
        // Not a C8 deletion, on the same reading `replaceFindingsForSample` below already works
        // on: what C8 protects is the clinical record and the model's own claims — the JPEG and
        // every prediction-backed row, rejections included — and a slot from an edit the medtech
        // has since revised is neither. The model's boxes are excluded by id rather than by
        // trusting the diff, so this cannot reach one even if the finding list is wrong.
        val modelBoxIds = frame.predictions.indices.map { detectionIdFor(sampleId, it) }.toSet()
        val written = detections.mapTo(mutableSetOf()) { it.detectionId }
        val stale = detectionDao.getDetectionsForSample(sampleId)
            .map { it.detectionId }
            .filter { it !in written && it !in modelBoxIds }
        if (stale.isNotEmpty()) {
            detectionDao.deleteDetectionsByIds(stale)
        }

        // Wholesale replace, so a species the medtech removed on re-open actually disappears
        // instead of lingering and inflating the count. Not a C8 deletion: a count is a current
        // statement, and the detections and the JPEG it protects are untouched. An empty list
        // is meaningful and expected — it is a clean field.
        findingDao.replaceFindingsForSample(
            sampleId = sampleId,
            findings = findings.toFindingRows().map { it.toFindingEntity(sampleId) },
        )

        syncSampleUseCase.invoke(sampleId)
        // Verification works offline by design, so the direct push above often cannot land.
        // The scheduler is what gets the sample up once there is a network, with backoff,
        // rather than it waiting for the next time someone opens Settings.
        syncScheduler.requestSync()

        sampleId
    }
}
