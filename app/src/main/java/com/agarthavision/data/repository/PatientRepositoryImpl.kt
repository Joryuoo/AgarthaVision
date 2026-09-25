package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.sync.SyncScheduler
import com.agarthavision.domain.usecase.patients.PatientSort
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [PatientRepository].
 *
 * A thin mapping layer over [PatientDao]: the visibility rule, the filter and the
 * creator-link transaction all live in the DAO's SQL, where they can be read against the
 * Supabase policy they mirror. Reimplementing any of that here would give the client a
 * second definition of who can see a patient.
 */
class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val syncScheduler: SyncScheduler,
) : PatientRepository {

    @Suppress("LongParameterList")
    override fun observePatients(
        userId: String,
        query: String,
        limit: Int,
        sort: PatientSort,
        sex: Sex?,
        barangayCode: String?,
        minBirthdate: Long?,
        maxBirthdate: Long?,
    ): Flow<List<Patient>> =
        // Always from the top: the list accumulates rather than paging, as Records and
        // Sessions do. The DAO keeps its offset for a caller that one day wants real pages.
        patientDao.observePatients(
            userId = userId,
            query = query,
            limit = limit,
            offset = 0,
            sort = sort.name,
            sex = sex?.remoteValue,
            barangayCode = barangayCode,
            minBirthdate = minBirthdate,
            maxBirthdate = maxBirthdate,
        ).map { entities ->
            entities.map { it.toDomain() }
        }

    @Suppress("LongParameterList")
    override fun observePatientCount(
        userId: String,
        query: String,
        sex: Sex?,
        barangayCode: String?,
        minBirthdate: Long?,
        maxBirthdate: Long?,
    ): Flow<Int> =
        patientDao.observePatientCount(
            userId = userId,
            query = query,
            sex = sex?.remoteValue,
            barangayCode = barangayCode,
            minBirthdate = minBirthdate,
            maxBirthdate = maxBirthdate,
        )

    override suspend fun getPatientById(patientId: String): Patient? =
        patientDao.getPatientById(patientId)?.toDomain()

    override fun observePatientById(patientId: String): Flow<Patient?> =
        patientDao.observePatientById(patientId).map { it?.toDomain() }

    /**
     * The link row is stamped with [Patient.createdAt] rather than a fresh clock reading:
     * the patient and its link come into existence at the same moment, and giving them
     * two different timestamps would invent a window in which the patient existed
     * unlinked — which is precisely the state the transaction exists to prevent.
     */
    override suspend fun insert(patient: Patient) {
        patientDao.insertPatientWithCreatorLink(
            patient = patient.toEntity(),
            linkedAt = patient.createdAt.toEpochMilli(),
        )
        // A patient is the first thing a session needs on the server, so the sooner this
        // lands the sooner everything under it can. Fire-and-forget: the row is already
        // committed, and a save must not fail because the network did.
        syncScheduler.requestSync()
    }

    override suspend fun findDuplicates(
        userId: String,
        lastname: String,
        firstname: String,
        middleName: String?,
        birthdate: LocalDate,
        sex: Sex,
        excludingId: String,
    ): List<Patient> = patientDao.findIdentityMatches(
        userId = userId,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        // Same conversion as Patient.toEntity(): birthdate at midnight in CLINICAL_ZONE.
        birthdateEpochMillis = birthdate.atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli(),
        sex = sex.remoteValue,
        excludingId = excludingId,
    ).map { it.toDomain() }

    /**
     * [Patient.toEntity] resets `supabase_status` to `pending`, which is what re-queues
     * the edited row for the next sync pass. `PatientDao.getPatientsPendingSync` picks it
     * up from there and the remote write is an upsert, so a patient that has already
     * synced once syncs again cleanly.
     */
    override suspend fun update(patient: Patient) {
        patientDao.updatePatient(patient.toEntity())
        syncScheduler.requestSync()
    }

    override suspend fun getExistingCodenamesByPrefix(userId: String, prefix: String): List<String> =
        patientDao.getExistingCodenamesByPrefix(userId, prefix)
}
