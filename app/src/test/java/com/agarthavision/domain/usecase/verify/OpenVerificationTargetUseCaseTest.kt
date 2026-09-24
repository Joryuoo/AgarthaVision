package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.mapper.addedDetectionIdFor
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.records.ResolveSampleImageSourceUseCase
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.util.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Reopening a sample for editing — including on a device that did not capture it.
 *
 * **"Annotate anytime" worked end to end only on the capturing device.** Everywhere else it
 * opened blank, and nothing errored: `SampleRemoteDataSource` writes `imagePath = ""`, so
 * `File("").readBytes()` threw and `getOrDefault(ByteArray(0))` swallowed it, and it writes
 * `predictionsJson = null`, so there were no boxes either. A missing image was indistinguishable
 * from an empty one. This suite is what keeps the recovery path honest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenVerificationTargetUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val findingDao: SampleSpeciesFindingDao = mock()
    private val resolveImageSource: ResolveSampleImageSourceUseCase = mock()

    private val useCase = OpenVerificationTargetUseCase(
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        findingDao = findingDao,
        resolveSampleImageSource = resolveImageSource,
        gson = Gson(),
    )

    private val sampleId = "sample-1"

    /** A sample as it arrives from Supabase: no local file, no predictions_json, no dimensions. */
    private fun syncedSample() = SampleEntity(
        sampleId = sampleId,
        sessionId = "session-1",
        userId = "user-1",
        deviceId = "device-b",
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        imagePath = "",
        storagePath = "user-1/$sampleId.jpg",
        inferenceModelVersion = "v2",
        needsReannotation = false,
        status = SampleStatus.SYNCED.value,
        userNote = null,
        isManual = false,
        predictionsJson = null,
        imageWidth = null,
        imageHeight = null,
    )

    // Seven independent knobs, each defaulted; a holder here would obscure which one a
    // given case varies. Same call as `topLeftOf` in DetectionOverlayGeometryTest.
    @Suppress("LongParameterList")
    private fun boxDetection(
        ordinal: Int,
        verdict: DetectionVerdict = DetectionVerdict.CONFIRMED,
        x: Float = 320f,
        y: Float = 240f,
        classLabel: String = "Ascaris",
        expertClass: String? = null,
    ) = DetectionEntity(
        detectionId = detectionIdFor(sampleId, ordinal),
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = 0.87f,
        bboxX = x,
        bboxY = y,
        bboxW = 40f,
        bboxH = 30f,
        verdict = verdict.value,
        expertClass = expertClass,
    )

    private suspend fun stub(
        detections: List<DetectionEntity>,
        predictionsJson: String? = null,
    ) {
        whenever(sampleDao.getSampleById(sampleId))
            .thenReturn(syncedSample().copy(predictionsJson = predictionsJson))
        whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(detections)
        whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(emptyList())
        whenever(resolveImageSource(any())).thenReturn(
            SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "user-1/$sampleId.jpg"),
        )
    }

    private fun addedDetection(species: String, slot: Int, x: Float? = null, stageKey: String? = null) =
        DetectionEntity(
            detectionId = addedDetectionIdFor(sampleId, species, slot, stageKey),
            sampleId = sampleId,
            classLabel = species,
            confidence = 1.0f,
            bboxX = x,
            bboxY = x,
            bboxW = x?.let { 12f },
            bboxH = x?.let { 12f },
            verdict = DetectionVerdict.CONFIRMED.value,
            expertClass = species,
        )

    /**
     * An added species comes back as **one card carrying the field total**, not as the remainder
     * it used to be rebuilt as — the stored total is the medtech's own assertion and survives
     * whatever happens to the boxes under it.
     */
    @Test
    fun `an added species reopens holding its field total and its drawn boxes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(sampleDao.getSampleById(sampleId)).thenReturn(syncedSample())
            whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(
                listOf(
                    boxDetection(0),
                    // Drawn boxes pack to the front; the third egg was never located.
                    addedDetection("Ascaris lumbricoides", 0, x = 10f),
                    addedDetection("Ascaris lumbricoides", 1, x = 80f),
                    addedDetection("Ascaris lumbricoides", 2),
                ),
            )
            whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(
                listOf(
                    SampleSpeciesFindingEntity(
                        findingId = "row-1",
                        sampleId = sampleId,
                        species = "Ascaris lumbricoides",
                        stage = null,
                        eggCount = 4,
                    ),
                ),
            )
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
            )

            val added = useCase(sampleId).getOrThrow().findings.single { it.prediction == null }

            assertEquals("The medtech's own total, carried across whole.", 4, added.answers.fieldTotal)
            assertEquals(
                "Both located eggs come back, and the undrawn one stops the walk.",
                listOf(10f, 80f),
                added.answers.drawnBoxes.map { it.x },
            )
        }

    /** A species the boxes already account for needs no card of its own. */
    @Test
    fun `a total the boxes cover reopens with no added card`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(sampleDao.getSampleById(sampleId)).thenReturn(syncedSample())
            whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(listOf(boxDetection(0)))
            whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(
                listOf(
                    SampleSpeciesFindingEntity(
                        findingId = "row-1",
                        sampleId = sampleId,
                        species = "Ascaris lumbricoides",
                        stage = null,
                        eggCount = 1,
                    ),
                ),
            )
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
            )

            val target = useCase(sampleId).getOrThrow()

            assertTrue(target.findings.none { it.prediction == null })
        }

    /**
     * The boxes come back, rebuilt from the detection rows — which *are* pulled down, and carry
     * the same centre-based geometry plus the model's own class and confidence.
     */
    @Test
    fun `a sample synced from another device still has its boxes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0), boxDetection(1, x = 100f)))

            val target = useCase(sampleId).getOrThrow()

            assertEquals(2, target.frame.predictions.size)
            assertEquals(320f, target.frame.predictions[0].x)
            assertEquals(100f, target.frame.predictions[1].x)
            assertEquals(
                "Nothing invented: the confidence is the model's own.",
                0.87f,
                target.frame.predictions[0].confidence,
            )
        }

    /** And a frame to draw them on, from Storage rather than from a local file that is not there. */
    @Test
    fun `it carries a way to load the image instead of zero bytes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0)))

            val target = useCase(sampleId).getOrThrow()

            assertTrue(target.frame.jpegBytes.isEmpty())
            assertTrue(target.imageSource is SampleImageSource.RemoteSignedUrl)
        }

    /**
     * Offline, with no local file and no signed URL, the screen has to be able to say so rather
     * than open a blank canvas the medtech might annotate into the void.
     */
    @Test
    fun `an unreachable image is reported as unavailable, not as an empty frame`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0)))
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.Unavailable(SampleImageUnavailableReason.REMOTE_LOAD_FAILED),
            )

            val target = useCase(sampleId).getOrThrow()

            assertTrue(target.imageSource is SampleImageSource.Unavailable)
        }

    /**
     * A BOX_INCORRECT row that carries a box carries the medtech's redraw — the mapper no longer
     * keeps the rejected one — so it reopens locked at "No" wherever the predictions came from,
     * here rebuilt from the rows themselves.
     */
    @Test
    fun `a box the model got wrong stays locked on a device that cannot compare geometry`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0, verdict = DetectionVerdict.BOX_INCORRECT)))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            assertEquals(false, answers.isBoxCorrect)
            assertTrue("Q2 must not be settable back to Yes here.", answers.boxReplaced)
        }

    /**
     * A row the model got right is not locked. Reconstruction must not turn every reopened
     * detection into a claimed localisation error.
     */
    @Test
    fun `a box the model got right is not marked replaced`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0, verdict = DetectionVerdict.CONFIRMED)))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            assertEquals(true, answers.isBoxCorrect)
            assertFalse(answers.boxReplaced)
        }

    /**
     * Reconstruction walks ordinals and stops at the first gap, so an egg the medtech added —
     * which keys on what they asserted, not on an ordinal — never becomes a model prediction.
     */
    @Test
    fun `an added egg is not mistaken for model output`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val added = DetectionEntity(
                detectionId = "$sampleId#finding#Hookworm",
                sampleId = sampleId,
                classLabel = "Hookworm",
                confidence = 1.0f,
                bboxX = null,
                bboxY = null,
                bboxW = null,
                bboxH = null,
                verdict = DetectionVerdict.CONFIRMED.value,
                expertClass = null,
            )
            stub(listOf(boxDetection(0), added))

            val target = useCase(sampleId).getOrThrow()

            assertEquals(1, target.frame.predictions.size)
        }

    // ── species+stage identity (14zcqnthz6e) ─────────────────────────────────

    /** Two added cards of one species at different stages reopen as two cards, boxes kept apart. */
    @Test
    fun `two added cards of different stages reopen as two cards with only their own boxes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(sampleDao.getSampleById(sampleId)).thenReturn(syncedSample())
            whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(
                listOf(
                    addedDetection("Ascaris lumbricoides", 0, x = 10f, stageKey = "CORTICATED_FERTILIZED"),
                    addedDetection("Ascaris lumbricoides", 1, x = 20f, stageKey = "CORTICATED_FERTILIZED"),
                    addedDetection("Ascaris lumbricoides", 0, x = 30f, stageKey = "DECORTICATED_FERTILIZED"),
                ),
            )
            whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(
                listOf(
                    SampleSpeciesFindingEntity(
                        findingId = "row-1",
                        sampleId = sampleId,
                        species = "Ascaris lumbricoides",
                        stage = "CORTICATED_FERTILIZED",
                        eggCount = 2,
                    ),
                    SampleSpeciesFindingEntity(
                        findingId = "row-2",
                        sampleId = sampleId,
                        species = "Ascaris lumbricoides",
                        stage = "DECORTICATED_FERTILIZED",
                        eggCount = 1,
                    ),
                ),
            )
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
            )

            val added = useCase(sampleId).getOrThrow().findings.filter { it.prediction == null }

            assertEquals(2, added.size)
            val cf = added.first { it.answers.stage == EggStage.CORTICATED_FERTILIZED }
            val df = added.first { it.answers.stage == EggStage.DECORTICATED_FERTILIZED }
            assertEquals(2, cf.answers.fieldTotal)
            assertEquals(listOf(10f, 20f), cf.answers.drawnBoxes.map { it.x })
            assertEquals(1, df.answers.fieldTotal)
            assertEquals(listOf(30f), df.answers.drawnBoxes.map { it.x })
        }

    /**
     * A card written before stage-aware ids existed used the species-only id. Reopening it must
     * still recover its drawn boxes rather than showing the right total with the geometry
     * silently missing.
     */
    @Test
    fun `a card written with the old species-only id still recovers its drawn boxes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(sampleDao.getSampleById(sampleId)).thenReturn(syncedSample())
            whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(
                listOf(addedDetection("Hookworm", 0, x = 15f)),
            )
            whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(
                listOf(
                    SampleSpeciesFindingEntity(
                        findingId = "row-1",
                        sampleId = sampleId,
                        species = "Hookworm",
                        stage = "CORTICATED_FERTILIZED",
                        eggCount = 1,
                    ),
                ),
            )
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
            )

            val added = useCase(sampleId).getOrThrow().findings.single { it.prediction == null }

            assertEquals(1, added.answers.fieldTotal)
            assertEquals(
                "The legacy id is the only place this box can be, and the fallback finds it.",
                listOf(15f),
                added.answers.drawnBoxes.map { it.x },
            )
        }

    // ── Q3 on read-back ──────────────────────────────────────────────────────
    //
    // `speciesConfirmed` is not a column. It has to be derived on reopen, and leaving it at its
    // null default drew every reopened row as "this is NOT an Ascaris egg" with no picker under
    // it to say otherwise.

    @Test
    fun `a species the medtech kept reopens confirmed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // expert_class null: the model said Ascaris, the medtech submitted without objecting.
            // Exactly the row the field test found broken.
            stub(listOf(boxDetection(0)))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            assertEquals(true, answers.speciesConfirmed)
            assertEquals(EggSpecies.ASCARIS, answers.species)
        }

    @Test
    fun `a species the medtech overrode reopens unconfirmed, so the picker is offered`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0, expertClass = "Hookworm")))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            // False, not null: false is what opens SpeciesDropdown, with their own choice in it.
            assertEquals(false, answers.speciesConfirmed)
            assertEquals(EggSpecies.HOOKWORM, answers.species)
        }

    @Test
    fun `a free-text species reopens as an override rather than as agreement`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Nothing maps "Fasciola hepatica" to an EggSpecies, so it lands on OTHER - which
            // must not compare equal to the model's Ascaris and read as a confirmation.
            stub(listOf(boxDetection(0, expertClass = "Fasciola hepatica")))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            assertEquals(false, answers.speciesConfirmed)
            assertEquals(EggSpecies.OTHER, answers.species)
            assertEquals("Fasciola hepatica", answers.otherSpeciesText)
        }

    @Test
    fun `a model class the app cannot map leaves nothing to confirm`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0, classLabel = "Strongyloides stercoralis")))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            // Null, and it must stay null: Q3 never renders, the picker is offered straight
            // away. This is the one case where the default was right by accident.
            assertNull(answers.speciesConfirmed)
        }

    @Test
    fun `a box the model misplaced still reopens with its species confirmed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Q2 "No" does not short-circuit Q3 - a box in the wrong place still holds a real
            // egg that has to be named - so the BOX_INCORRECT branch needs the flag too.
            stub(listOf(boxDetection(0, verdict = DetectionVerdict.BOX_INCORRECT)))

            val answers = useCase(sampleId).getOrThrow().findings[0].answers

            assertEquals(false, answers.isBoxCorrect)
            assertEquals(true, answers.speciesConfirmed)
        }

    @Test
    fun `an added species has nothing to confirm`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // A human assertion with no model claim behind it. Null is correct here and the
            // sheet relies on it: with no prediction there is no suggestion, so Q3 never
            // renders and SpeciesDropdown is shown unconditionally.
            whenever(sampleDao.getSampleById(sampleId)).thenReturn(syncedSample())
            whenever(detectionDao.getDetectionsForSample(sampleId)).thenReturn(
                listOf(addedDetection("Hookworm", 0)),
            )
            whenever(findingDao.getFindingsForSample(sampleId)).thenReturn(
                listOf(
                    SampleSpeciesFindingEntity(
                        findingId = "row-1",
                        sampleId = sampleId,
                        species = "Hookworm",
                        stage = null,
                        eggCount = 1,
                    ),
                ),
            )
            whenever(resolveImageSource(any())).thenReturn(
                SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
            )

            val added = useCase(sampleId).getOrThrow().findings.single { it.prediction == null }

            assertNull(added.answers.speciesConfirmed)
        }

    // ── Box provenance (14zcqnthrx6 / 14zcqnthrx8) ───────────────────────────

    /** The model's output as the capturing device, or a pull from `predictions`, stores it. */
    private fun predictionsJson(vararg xs: Float): String = Gson().toJson(
        xs.map { x ->
            PredictionDto(classLabel = "Ascaris", confidence = 0.87f, x = x, y = 240f, width = 40f, height = 30f)
        },
    )

    private fun rejectedUnredrawn(ordinal: Int) = boxDetection(ordinal, verdict = DetectionVerdict.BOX_INCORRECT)
        .copy(bboxX = null, bboxY = null, bboxW = null, bboxH = null)

    /**
     * A box the medtech said is misplaced and did not redraw is stored with no geometry. It
     * reopens as exactly that: Q2 "No", nothing drawn, and Q2 free — nobody replaced anything.
     */
    @Test
    fun `a rejected box nobody redrew reopens with Q2 No and nothing drawn`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(rejectedUnredrawn(0)), predictionsJson = predictionsJson(320f))

            val finding = useCase(sampleId).getOrThrow().findings.single()

            assertEquals(320f, finding.prediction?.x)
            assertEquals(false, finding.answers.isBoxCorrect)
            assertNull(finding.answers.drawnBox)
            assertFalse(finding.answers.boxReplaced)
        }

    /**
     * On BOX_INCORRECT a stored box *is* the redraw, read off the row. The old comparison
     * against the model's geometry is gone, so this holds however close the redraw sits to it.
     */
    @Test
    fun `a stored box on a BOX_INCORRECT row is the redraw`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(
                listOf(boxDetection(0, verdict = DetectionVerdict.BOX_INCORRECT, x = 320.2f)),
                predictionsJson = predictionsJson(320f),
            )

            val answers = useCase(sampleId).getOrThrow().findings.single().answers

            assertTrue(answers.boxReplaced)
            assertEquals(320.2f, answers.drawnBox?.x)
        }

    /**
     * **The regression the rebuild would otherwise introduce.** A rejected box with no geometry
     * in the middle of the frame must not be skipped: that would slide ordinal 2 into slot 1,
     * reopen it against ordinal 1's detection, and write its ruling onto the wrong row on
     * re-submit. The rebuild stops instead, and every prediction it does return sits on its own
     * ordinal.
     */
    @Test
    fun `rebuilding predictions stops at a row with no box rather than shifting later ones`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(listOf(boxDetection(0, x = 100f), rejectedUnredrawn(1), boxDetection(2, x = 300f)))

            val target = useCase(sampleId).getOrThrow()

            assertEquals(listOf(100f), target.frame.predictions.map { it.x })
            assertEquals(100f, target.findings.single { it.prediction != null }.prediction?.x)
        }

    /** With the model's output on the device, nothing is rebuilt and every box is present. */
    @Test
    fun `stored predictions are used whole even when a row has no box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            stub(
                listOf(boxDetection(0, x = 100f), rejectedUnredrawn(1), boxDetection(2, x = 300f)),
                predictionsJson = predictionsJson(100f, 200f, 300f),
            )

            val findings = useCase(sampleId).getOrThrow().findings

            assertEquals(listOf(100f, 200f, 300f), findings.map { it.prediction?.x })
            assertEquals(listOf(true, false, true), findings.map { it.answers.isBoxCorrect })
        }
}
