package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.ColleagueDao
import com.agarthavision.domain.repository.ColleagueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room-backed [ColleagueRepository], filled by `FetchRemoteDataUseCase`. */
class ColleagueRepositoryImpl @Inject constructor(
    private val colleagueDao: ColleagueDao,
) : ColleagueRepository {
    override suspend fun nameOf(userId: String): String? =
        colleagueDao.getFullName(userId)?.takeIf { it.isNotBlank() }

    override fun observeNames(): Flow<Map<String, String?>> =
        colleagueDao.observeColleagues().map { rows ->
            rows.associate { it.userId to it.fullName?.takeIf(String::isNotBlank) }
        }
}
