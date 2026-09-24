package com.agarthavision.domain.usecase.sync

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.core.sync.FetchOutcomeStore
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.inference.encodePredictions
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.mapper.SamplePrediction
import com.agarthavision.data.local.mapper.toDetectionEntities
import com.agarthavision.data.local.mapper.toSamplePredictions
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.data.supabase.PatientRemoteDataSource
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.usecase.records.ResolveSampleImageSourceUseCase
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.OpenVerificationTargetUseCase
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The model's output across a sync, end to end on the device: a real Room database, the real
 * pull, the real reopen path, the real verdict mapper. Only the Supabase client is faked, and it
 * answers with exactly the rows the push side writes for the same frame.
 *
 * What the unit suites cannot show between them is the one property this change is for: a frame
 * with a rejected box nobody redrew, and a redrawn one, reopened on a device that did not capture
 * it, draws the model's real boxes and reads each ruling back onto the box it was made about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredictionsSyncRoundTripTest {

    private lateinit var db: AgarthaDatabase
    private val gson = Gson()

    private val authRepository: AuthRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock()
    private val patientRemote: PatientRemoteDataSource = mock()
    private val sessionRemote: SessionRemoteDataSource = mock()
    private val reportRemote: ReportRemoteDataSource = mock()
    private val sampleRemote: SampleRemoteDataSource = mock()
    private val sampleImageStore: SampleImageStore = mock()
    private val cacheSampleImages: CacheSampleImagesUseCase = mock {
        onBlocking { invoke(any()) } doReturn ImageCacheSummary()
    }
    private val resolveImageSource: ResolveSampleImageSourceUseCase = mock()

    private val modelOutput = listOf(
        prediction(x = 100f),
        prediction(x = 200f),
        prediction(x = 300f),
    )
    private val redraw = ImageBox(x = 305f, y = 250f, width = 50f, height = 45f)

    @Before
    fun setUp() = runTest {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedSession()

        whenever(authRepository.currentLocalUserId()).thenReturn(USER_ID)
        whenever(authRepository.isAuthenticated()).thenReturn(true)
        whenever(connectivityObserver.currentlyOnline()).thenReturn(true)
        whenever(patientRemote.fetchPatients()).thenReturn(emptyList())
        whenever(patientRemote.fetchPatientLinks()).thenReturn(emptyList())
        whenever(sessionRemote.fetchSessions(USER_ID)).thenReturn(emptyList())
        whenever(reportRemote.fetchReports(USER_ID)).thenReturn(emptyList())
        whenever(sampleRemote.fetchFindings(any())).thenReturn(emptyList())
        whenever(sampleImageStore.cachedPathOrNull(any(), any())).thenReturn(null)
        whenever(resolveImageSource(any())).thenReturn(
            SampleImageSource.RemoteSignedUrl(url = "https://signed", cacheKey = "k"),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a sample verified elsewhere reopens with the model's boxes and each ruling on its own box`() = runTest {
        serverHolds(
            predictions = modelOutput.toSamplePredictions(SAMPLE_ID),
            detections = rulingsOnTheFrame(),
        )

        fetch()

        val target = reopen()
        assertEquals(listOf(100f, 200f, 300f), target.frame.predictions.map { it.x })

        val (kept, rejected, redrawn) = target.findings.filter { it.prediction != null }.map { it.answers }
        assertEquals(true, kept.isBoxCorrect)
        assertFalse(kept.boxReplaced)

        assertEquals(false, rejected.isBoxCorrect)
        assertFalse("A rejection nobody redrew is not a redraw.", rejected.boxReplaced)
        assertNull(rejected.drawnBox)

        assertEquals(false, redrawn.isBoxCorrect)
        assertTrue(redrawn.boxReplaced)
        assertEquals(redraw, redrawn.drawnBox)
    }

    /** The stored rows themselves: the rejected box is gone, the model's lives in predictions. */
    @Test
    fun `the rejected box is stored as no box and the redraw as the medtech's`() = runTest {
        serverHolds(
            predictions = modelOutput.toSamplePredictions(SAMPLE_ID),
            detections = rulingsOnTheFrame(),
        )

        fetch()

        val boxes = db.detectionDao().getDetectionsForSample(SAMPLE_ID)
            .sortedBy { it.bboxX ?: Float.MAX_VALUE }
            .map { it.bboxX }
        assertEquals(listOf(100f, 305f, null), boxes)
    }

    /**
     * The capturing device, after its push landed: the server row has no predictions_json, and
     * here the server holds no predictions either (a sample pushed before the table existed).
     * The pull used to null the column; the device's own copy must survive.
     */
    @Test
    fun `the capturing device keeps its model output through a pull`() = runTest {
        val json = gson.encodePredictions(modelOutput)
        db.sampleDao().upsertSample(sample(status = SampleStatus.SYNCED.value).copy(predictionsJson = json))
        db.detectionDao().insertDetections(rulingsOnTheFrame())
        serverHolds(predictions = emptyList(), detections = rulingsOnTheFrame())

        fetch()

        assertEquals(json, db.sampleDao().getSampleById(SAMPLE_ID)?.predictionsJson)
        assertEquals(listOf(100f, 200f, 300f), reopen().frame.predictions.map { it.x })
    }

    /**
     * Without the model's output anywhere, the rebuild from detection rows stops at the rejected
     * box rather than sliding the redrawn one into its slot.
     */
    @Test
    fun `without predictions the rebuild never shifts a ruling onto the wrong box`() = runTest {
        serverHolds(predictions = emptyList(), detections = rulingsOnTheFrame())

        fetch()

        val target = reopen()
        assertEquals(listOf(100f), target.frame.predictions.map { it.x })
        assertEquals(true, target.findings.single { it.prediction != null }.answers.isBoxCorrect)
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    /** What the capturing device writes for the frame: kept, rejected-not-redrawn, redrawn. */
    private fun rulingsOnTheFrame(): List<DetectionEntity> = listOf(
        Finding(
            prediction = modelOutput[0],
            answers = VerificationAnswers(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS),
        ),
        Finding(
            prediction = modelOutput[1],
            answers = VerificationAnswers(isEgg = true, isBoxCorrect = false, species = EggSpecies.ASCARIS),
        ),
        Finding(
            prediction = modelOutput[2],
            answers = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = EggSpecies.ASCARIS,
                drawnBox = redraw,
                boxReplaced = true,
            ),
        ),
    ).toDetectionEntities(SAMPLE_ID)

    private suspend fun serverHolds(predictions: List<SamplePrediction>, detections: List<DetectionEntity>) {
        // The server's sample row, as SampleRemoteDataSource maps it: no model output.
        whenever(sampleRemote.fetchSamples(USER_ID, 0L, 500L))
            .thenReturn(listOf(sample(status = SampleStatus.SYNCED.value)))
        whenever(sampleRemote.fetchPredictions(listOf(SAMPLE_ID))).thenReturn(predictions)
        whenever(sampleRemote.fetchDetections(listOf(SAMPLE_ID))).thenReturn(detections)
    }

    private suspend fun fetch() {
        val summary = FetchRemoteDataUseCase(
            authRepository = authRepository,
            connectivityObserver = connectivityObserver,
            patientRemoteDataSource = patientRemote,
            sampleRemoteDataSource = sampleRemote,
            sessionRemoteDataSource = sessionRemote,
            reportRemoteDataSource = reportRemote,
            patientDao = db.patientDao(),
            sessionDao = db.sessionDao(),
            sampleDao = db.sampleDao(),
            detectionDao = db.detectionDao(),
            sampleSpeciesFindingDao = db.sampleSpeciesFindingDao(),
            reportDao = db.reportDao(),
            initialFetchStateStore = mock<InitialFetchStateStore>(),
            fetchOutcomeStore = mock<FetchOutcomeStore>(),
            speciesSuggestionSeeder = mock<SpeciesSuggestionSeeder>(),
            cacheSampleImages = cacheSampleImages,
            sampleImageStore = sampleImageStore,
            gson = gson,
        ).invoke().getOrThrow() as FetchSummary.Ran
        assertTrue("the pull must succeed: ${summary.failed}", FetchType.SAMPLES !in summary.failed)
    }

    private suspend fun reopen() = OpenVerificationTargetUseCase(
        sampleDao = db.sampleDao(),
        detectionDao = db.detectionDao(),
        findingDao = db.sampleSpeciesFindingDao(),
        resolveSampleImageSource = resolveImageSource,
        gson = gson,
    ).invoke(SAMPLE_ID).getOrThrow()

    private fun prediction(x: Float) = Prediction(
        classLabel = "Ascaris",
        confidence = 0.8f,
        x = x,
        y = 240f,
        width = 40f,
        height = 30f,
    )

    private suspend fun seedSession() {
        db.patientDao().upsertPatient(
            PatientEntity(
                patientId = PATIENT_ID,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = USER_ID,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        db.sessionDao().upsertSession(
            SessionEntity(
                sessionId = SESSION_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 1_000L,
            ),
        )
    }

    private fun sample(status: String) = SampleEntity(
        sampleId = SAMPLE_ID,
        sessionId = SESSION_ID,
        userId = USER_ID,
        deviceId = "",
        timestamp = 1_500L,
        verifiedAt = 2_000L,
        imagePath = "",
        storagePath = "$USER_ID/$SAMPLE_ID.jpg",
        status = status,
    )

    private companion object {
        const val USER_ID = "user-a"
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
        const val SAMPLE_ID = "smp-1"
    }
}
