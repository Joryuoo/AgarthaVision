package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.domain.repository.PatientAccessRepository
import javax.inject.Inject

/**
 * Room-backed [PatientAccessRepository]. The links it reads are the ones
 * `FetchRemoteDataUseCase` keeps in step with the server, revocations included.
 */
class PatientAccessRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
) : PatientAccessRepository {
    override suspend fun isAssignedToSessionPatient(sessionId: String, userId: String): Boolean =
        patientDao.isSessionPatientLinkedToUser(sessionId, userId)
}
