package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.mapper.addedDetectionIdFor
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import com.agarthavision.domain.sync.RecordingSyncScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitVerificationUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDao: SampleDao = mock()
    // Room returns an empty list for a sample with no detection rows, never null, but
    // Mockito's default for a suspend function is null. Unstubbed, the prune block in
    // SubmitVerificationUseCase throws an NPE that runCatching swallows, so every assertion
    // sited after it passes vacuously instead of failing.
    private val detectionDao: DetectionDao = mock {
        onBlocking { getDetectionsForSample(any()) } doReturn emptyList()
    }
    private val syncSampleUseCase: SyncSampleUseCase = mock()

    private val findingDao: SampleSpeciesFindingDao = mock()

    private val syncScheduler = RecordingSyncScheduler()

    private val useCase = SubmitVerificationUseCase(
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        findingDao = findingDao,
        syncSampleUseCase = syncSampleUseCase,
        syncScheduler = syncScheduler,
    )

    private val prediction = Prediction(
        classLabel = "Ascaris",
        confidence = 0.9f,
        x = 100f,
        y = 200f,
        width = 50f,
        height = 60f,
    )

    private val frame = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(10),
        predictions = listOf(prediction),
        inferenceModelVersion = "v2",
    )

    @Test
    fun `submitting a verification asks for a sync pass`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Verification works offline by design, so syncSampleUseCase often cannot land.
            // The request is what gets the sample up once there is a network, with backoff.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            useCase(frame, findings = emptyList(), missedEgg = false)

            assertEquals(1, syncScheduler.requests)
        }

    @Test
    fun `submit persists sample with VERIFIED status and one detection per box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val findings = listOf(
                Finding(
                    prediction,
                    VerificationAnswers(
                        isEgg = true,
                        isBoxCorrect = true,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            )
            val result = useCase(frame, findings, missedEgg = null)
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(false),
                userNote = isNull(),
            )
            verify(detectionDao).insertDetections(any())
        }

    @Test
    fun `submit with all FALSE_POSITIVE still writes sample row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val findings = listOf(Finding(prediction, VerificationAnswers(isEgg = false)))
            val result = useCase(frame, findings, missedEgg = null)
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(false),
                userNote = isNull(),
            )
        }

    @Test
    fun `Q4 yes sets needsReannotation true on sample row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val findings = listOf(
                Finding(
                    prediction,
                    VerificationAnswers(
                        isEgg = true,
                        isBoxCorrect = true,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            )
            useCase(frame, findings, missedEgg = true)
            advanceUntilIdle()

            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(true),
                userNote = isNull(),
            )
        }

    @Test
    fun `re-submitting an edited sample replaces its detections instead of appending`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // The dangerous one. Detection ids used to be random, so a second save inserted a
            // whole second set beside the first - doubling every egg count with no error
            // anywhere. Derived ids plus the DAO REPLACE strategy make a re-save an overwrite.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))
            val findings = listOf(
                Finding(
                    prediction,
                    VerificationAnswers(
                        isEgg = true,
                        isBoxCorrect = true,
                        species = EggSpecies.ASCARIS,
                    ),
                ),
            )

            useCase(frame, findings, missedEgg = null)
            useCase(frame, findings, missedEgg = null)
            advanceUntilIdle()

            val captor = argumentCaptor<List<DetectionEntity>>()
            verify(detectionDao, times(2)).insertDetections(captor.capture())
            val firstIds = captor.firstValue.map { it.detectionId }
            val secondIds = captor.secondValue.map { it.detectionId }
            assertEquals(
                "A re-save must land on the same rows, not create new ones.",
                firstIds,
                secondIds,
            )
        }

    @Test
    fun `lowering an added count prunes the slots it left behind, and never a model box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // 23 Ascaris counted down to 21 leaves two null-bbox rows for eggs nobody claims.
            // insertDetections replaces and never deletes, so without this they stay forever,
            // inflating the corpus and marking the frame un-localised for good.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))
            val ascaris = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = EggSpecies.ASCARIS,
            )
            val stale = listOf(
                addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 1),
                addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 2),
            )
            whenever(detectionDao.getDetectionsForSample("sample-1")).thenReturn(
                (stale + detectionIdFor("sample-1", 0)).map { id ->
                    DetectionEntity(
                        detectionId = id,
                        sampleId = "sample-1",
                        classLabel = "Ascaris lumbricoides",
                        confidence = 1.0f,
                        bboxX = null,
                        bboxY = null,
                        bboxW = null,
                        bboxH = null,
                        verdict = DetectionVerdict.CONFIRMED.value,
                    )
                },
            )

            // One box kept, and a total of two: exactly one egg left unboxed, so slot 0 only.
            val findings = listOf(
                Finding(prediction, ascaris),
                Finding(prediction = null, answers = ascaris.copy(fieldTotal = 2)),
            )
            useCase(frame, findings, missedEgg = true)
            advanceUntilIdle()

            val captor = argumentCaptor<List<String>>()
            verify(detectionDao).deleteDetectionsByIds(captor.capture())
            assertEquals(stale, captor.firstValue)
        }

    @Test
    fun `a model box is never pruned, whatever the finding list says`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // C8's boundary, asserted rather than trusted: a rejection is kept as a labelled
            // FALSE_POSITIVE row, so no path through here may delete a prediction-backed id.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))
            val boxId = detectionIdFor("sample-1", 0)
            whenever(detectionDao.getDetectionsForSample("sample-1")).thenReturn(
                listOf(
                    DetectionEntity(
                        detectionId = boxId,
                        sampleId = "sample-1",
                        classLabel = "Ascaris lumbricoides",
                        confidence = 0.9f,
                        bboxX = 1f,
                        bboxY = 2f,
                        bboxW = 3f,
                        bboxH = 4f,
                        verdict = DetectionVerdict.CONFIRMED.value,
                    ),
                ),
            )

            // An empty finding list produces no entities at all, so the box id is "unwritten".
            useCase(frame, findings = emptyList(), missedEgg = null)
            advanceUntilIdle()

            verify(detectionDao, never()).deleteDetectionsByIds(any())
        }

    @Test
    fun `submitting re-arms sync by putting the sample back to verified`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // An already-SYNCED sample has to re-enter getSamplesPendingSync, or an edit made
            // offline would never reach Supabase at all.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            useCase(frame, listOf(Finding(prediction, VerificationAnswers(isEgg = false))), missedEgg = null)
            advanceUntilIdle()

            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(false),
                userNote = isNull(),
            )
        }

    /**
     * **Stale-row cleanup generalizes to the sentinel id (14zcqnthz6e follow-up).** A non-primary
     * card reopened with no stage - and therefore filed under [UNSTAGED_ADDED_STAGE_KEY] - later
     * gets a real stage filled in before this submit. The new stage-aware id is a different
     * detection id than the old sentinel one, so the old placeholder row must be recognised as
     * stale (not in the newly written set, not a model box id) and pruned by the same generic
     * cleanup that already handles a lowered added count.
     */
    @Test
    fun `a non-primary card whose stage is filled in before submit sheds its old sentinel row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val oldSentinelId = com.agarthavision.data.local.mapper.addedDetectionIdFor(
                "sample-1",
                "Ascaris lumbricoides",
                0,
                com.agarthavision.data.local.mapper.UNSTAGED_ADDED_STAGE_KEY,
            )
            val primaryId = addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 0, stageKey = null)
            whenever(detectionDao.getDetectionsForSample("sample-1")).thenReturn(
                listOf(oldSentinelId, primaryId).map { id ->
                    DetectionEntity(
                        detectionId = id,
                        sampleId = "sample-1",
                        classLabel = "Ascaris lumbricoides",
                        confidence = 1.0f,
                        bboxX = null,
                        bboxY = null,
                        bboxW = null,
                        bboxH = null,
                        verdict = DetectionVerdict.CONFIRMED.value,
                    )
                },
            )

            val primary = Finding(
                prediction = null,
                answers = VerificationAnswers(
                    species = com.agarthavision.domain.model.EggSpecies.ASCARIS,
                    stage = com.agarthavision.domain.model.EggStage.CORTICATED_FERTILIZED,
                    fieldTotal = 1,
                    isPrimaryAdded = true,
                ),
            )
            // Previously unstaged and non-primary; now given a real stage before this submit.
            val nowStaged = Finding(
                prediction = null,
                answers = VerificationAnswers(
                    species = com.agarthavision.domain.model.EggSpecies.ASCARIS,
                    stage = com.agarthavision.domain.model.EggStage.DECORTICATED_FERTILIZED,
                    fieldTotal = 1,
                    isPrimaryAdded = false,
                ),
            )

            useCase(frame, listOf(primary, nowStaged), missedEgg = null)
            advanceUntilIdle()

            val newStageId = addedDetectionIdFor(
                "sample-1",
                "Ascaris lumbricoides",
                0,
                stageKey = "DECORTICATED_FERTILIZED",
            )
            val insertCaptor = argumentCaptor<List<DetectionEntity>>()
            verify(detectionDao).insertDetections(insertCaptor.capture())
            assertEquals(
                "The card now writes under its real stage segment, not the sentinel.",
                setOf(primaryId, newStageId),
                insertCaptor.firstValue.map { it.detectionId }.toSet(),
            )

            val deleteCaptor = argumentCaptor<List<String>>()
            verify(detectionDao).deleteDetectionsByIds(deleteCaptor.capture())
            assertEquals(
                "The old sentinel-id row is stale now that this card writes under a real stage.",
                listOf(oldSentinelId),
                deleteCaptor.firstValue,
            )
        }

    @Test
    fun `removing a species replaces the findings rows wholesale`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Replace rather than upsert: a species the medtech removed on re-open has to
            // actually disappear, or it lingers and inflates the count.
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            useCase(frame, listOf(Finding(prediction, VerificationAnswers(isEgg = false))), missedEgg = null)
            advanceUntilIdle()

            // A rejected box counts nothing, so the sample ends with no findings rows at all.
            verify(findingDao).replaceFindingsForSample(eq("sample-1"), eq(emptyList()))
        }

}
