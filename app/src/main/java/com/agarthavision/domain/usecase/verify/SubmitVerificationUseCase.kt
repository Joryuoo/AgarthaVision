package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.mapper.toDetectionEntity
import com.agarthavision.data.local.mapper.toFindingEntity
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.LocationProvider
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
    private val locationProvider: LocationProvider,
    private val syncSampleUseCase: SyncSampleUseCase,
) {
    suspend operator fun invoke(
        frame: FlaggedFrame,
        findings: List<Finding>,
        missedEgg: Boolean?,
        userNote: String? = null,
        isRepeat: Boolean = false,
    ): Result<String> = runCatching {
        // Per ADR-007, verification works offline. No auth is required — the sample keeps
        // its existing owner (cached identity or null) and is claimed at the next login.
        val sampleId = frame.sampleId
        require(sampleId.isNotBlank()) { "Flagged sample id is required." }
        val location = locationProvider.getCurrentLocation()
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
            isRepeat = isRepeat,
            gpsLatitude = location?.latitude,
            gpsLongitude = location?.longitude,
            gpsAccuracy = location?.accuracyMeters,
        )

        // A rejected box still persists, as a labelled FALSE_POSITIVE row — that is what makes
        // detections a retraining corpus instead of a results table (C8). So every finding is
        // written, not just the ones that counted.
        detectionDao.insertDetections(
            findings.mapIndexed { ordinal, finding -> finding.toDetectionEntity(sampleId, ordinal) },
        )

        // Wholesale replace, so a species the medtech removed on re-open actually disappears
        // instead of lingering and inflating the count. Not a C8 deletion: a count is a current
        // statement, and the detections and the JPEG it protects are untouched. An empty list
        // is meaningful and expected — it is a clean field.
        findingDao.replaceFindingsForSample(
            sampleId = sampleId,
            findings = findings.toFindingRows().map { it.toFindingEntity(sampleId) },
        )

        syncSampleUseCase.invoke(sampleId)

        sampleId
    }
}
