package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.PatientSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncUseCasesTest {

    private val patientDao: PatientDao = mock()
    private val sessionDao: SessionDao = mock()
    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val findingDao: SampleSpeciesFindingDao = mock()
    private val reportDao: ReportDao = mock()

    private val patientRemoteDataSource: PatientRemoteDataSource = mock()
    private val sessionRemoteDataSource: SessionRemoteDataSource = mock()
    private val sampleRemoteDataSource: SampleRemoteDataSource = mock()
    private val reportRemoteDataSource: ReportRemoteDataSource = mock()
    private val reportFileStore: ReportFileStore = mock()
    private val gson = Gson()

    private val syncPatientUseCase = SyncPatientUseCase(
        patientDao = patientDao,
        remoteDataSource = patientRemoteDataSource,
    )

    private val syncSessionUseCase = SyncSessionUseCase(
        sessionDao = sessionDao,
        remoteDataSource = sessionRemoteDataSource,
        syncPatientUseCase = syncPatientUseCase,
    )

    private val syncSampleUseCase = SyncSampleUseCase(
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        findingDao = findingDao,
        remoteDataSource = sampleRemoteDataSource,
        syncSessionUseCase = syncSessionUseCase,
        gson = gson,
    )

    private val syncReportUseCase = SyncReportUseCase(
        reportDao = reportDao,
        remoteDataSource = reportRemoteDataSource,
        reportFileStore = reportFileStore,
    )

    @Before
    fun setUp() = runTest {
        whenever(detectionDao.getDetectionsForSample(any())).thenReturn(emptyList())
        whenever(findingDao.getFindingsForSample(any())).thenReturn(emptyList())
    }

    @Test
    fun `SyncSampleUseCase syncs parent session and patient prior to pushing sample`() = runTest {
        val imageFile = java.io.File.createTempFile("test_sample", ".jpg").apply {
            writeBytes(ByteArray(10))
            deleteOnExit()
        }

        val patient = PatientEntity(
            patientId = "pat-1",
            lastname = "Doe",
            firstname = "John",
            middleName = null,
            sex = "M",
            birthdate = 0L,
            psgcBarangayCode = "0102801001",
            createdBy = "user-1",
            createdAt = 1000L,
            updatedAt = 1000L,
            supabaseStatus = PatientSyncStatus.PENDING.value,
        )
        val session = SessionEntity(
            sessionId = "sess-1",
            userId = "user-1",
            patientId = "pat-1",
            deviceId = "dev-1",
            startedAt = 1000L,
            supabaseStatus = SessionSyncStatus.PENDING.value,
        )
        val sample = SampleEntity(
            sampleId = "smp-1",
            sessionId = "sess-1",
            userId = "user-1",
            deviceId = "dev-1",
            timestamp = 1000L,
            verifiedAt = 1000L,
            imagePath = imageFile.absolutePath,
            status = SampleStatus.VERIFIED.value,
        )

        whenever(patientDao.getPatientById("pat-1")).thenReturn(patient)
        whenever(sessionDao.getSessionById("sess-1")).thenReturn(session)
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-1")).thenReturn(sample)

        whenever(sampleRemoteDataSource.syncSample(any(), any(), any(), any(), any()))
            .thenReturn("user-1/smp-1.jpg")

        // Act
        val result = syncSampleUseCase("smp-1")

        // Assert
        assertTrue(result.isSuccess)
        val order = inOrder(patientRemoteDataSource, sessionRemoteDataSource, sampleRemoteDataSource)
        order.verify(patientRemoteDataSource).upsertPatient(patient)
        order.verify(sessionRemoteDataSource).upsertSession(session)
        order.verify(sampleRemoteDataSource).syncSample(any(), any(), any(), any(), any())
    }

    @Test
    fun `SyncSampleUseCase marks sample SYNC_FAILED on failure`() = runTest {
        val sample = SampleEntity(
            sampleId = "smp-2",
            sessionId = "sess-2",
            userId = "user-1",
            deviceId = "dev-1",
            timestamp = 1000L,
            verifiedAt = 1000L,
            imagePath = "",
            status = SampleStatus.VERIFIED.value,
        )
        whenever(sampleDao.getSampleByIdIncludingDeleted("smp-2")).thenReturn(sample)
        whenever(sessionDao.getSessionById("sess-2")).thenReturn(null) // Missing parent session

        val result = syncSampleUseCase("smp-2")

        assertTrue(result.isFailure)
        verify(sampleDao).updateStatus("smp-2", SampleStatus.SYNC_FAILED.value)
    }

    @Test
    fun `SyncPatientUseCase marks patient SYNC_FAILED on remote failure`() = runTest {
        val patient = PatientEntity(
            patientId = "pat-2",
            lastname = "Doe",
            firstname = "Jane",
            middleName = null,
            sex = "F",
            birthdate = 0L,
            psgcBarangayCode = "0102801001",
            createdBy = "user-1",
            createdAt = 1000L,
            updatedAt = 1000L,
            supabaseStatus = PatientSyncStatus.PENDING.value,
        )
        whenever(patientDao.getPatientById("pat-2")).thenReturn(patient)
        whenever(patientRemoteDataSource.upsertPatient(patient)).thenThrow(RuntimeException("Network error"))

        val result = syncPatientUseCase("pat-2")

        assertTrue(result.isFailure)
        verify(patientDao).updateSyncStatus("pat-2", PatientSyncStatus.SYNC_FAILED.value)
    }

    @Test
    fun `SyncReportUseCase marks report SYNC_FAILED on remote failure`() = runTest {
        val report = com.agarthavision.data.local.entity.ReportEntity(
            reportId = "rep-1",
            sessionId = "sess-1",
            userId = "user-1",
            generatedAt = 1000L,
            totalSamples = 1,
            totalEggsConfirmed = 1,
            positiveSpeciesJson = "[]",
            lpfPerSpeciesJson = "{}",
            csvFilePath = null,
            pdfFilePath = null,
            supabaseStatus = com.agarthavision.domain.model.ReportSyncStatus.PENDING.value,
            createdAt = 1000L,
        )
        whenever(reportDao.getReportById("rep-1")).thenReturn(report)
        whenever(reportRemoteDataSource.upsertReport(report)).thenThrow(RuntimeException("Upload error"))

        val result = syncReportUseCase("rep-1")

        assertTrue(result.isFailure)
        verify(reportDao).updateSupabaseStatus("rep-1", com.agarthavision.domain.model.ReportSyncStatus.SYNC_FAILED.value)
    }
}
