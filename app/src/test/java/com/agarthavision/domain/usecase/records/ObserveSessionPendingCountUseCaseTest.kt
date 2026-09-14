package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSessionPendingCountUseCaseTest {

    @Test
    fun `emits 0 when no user is authenticated`() = runTest {
        val useCase = ObserveSessionPendingCountUseCase(
            authRepository = FakeCountAuthRepository(userId = null),
            sampleRepository = FakeCountSampleRepository(),
        )

        val count = useCase("session-1").first()

        assertEquals(0, count)
    }

    @Test
    fun `emits list size when user is present`() = runTest {
        val useCase = ObserveSessionPendingCountUseCase(
            authRepository = FakeCountAuthRepository(userId = "user-1"),
            sampleRepository = FakeCountSampleRepository(
                flagged = MutableStateFlow(listOf(pendingSample("s1"), pendingSample("s2"), pendingSample("s3"))),
            ),
        )

        val count = useCase("session-1").first()

        assertEquals(3, count)
    }

    @Test
    fun `emits 0 when flagged list is empty`() = runTest {
        val useCase = ObserveSessionPendingCountUseCase(
            authRepository = FakeCountAuthRepository(userId = "user-1"),
            sampleRepository = FakeCountSampleRepository(
                flagged = MutableStateFlow(emptyList()),
            ),
        )

        val count = useCase("session-1").first()

        assertEquals(0, count)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `re-emits when the flagged flow emits a new list`() = runTest {
        val flaggedFlow = MutableStateFlow(listOf(pendingSample("s1")))
        val useCase = ObserveSessionPendingCountUseCase(
            authRepository = FakeCountAuthRepository(userId = "user-1"),
            sampleRepository = FakeCountSampleRepository(flagged = flaggedFlow),
        )

        val results = mutableListOf<Int>()
        val job = launch { useCase("session-1").collect { results += it } }
        // Let the collector start and pick up the initial StateFlow value.
        advanceUntilIdle()

        flaggedFlow.value = listOf(pendingSample("s1"), pendingSample("s2"))
        advanceUntilIdle()

        flaggedFlow.value = listOf(pendingSample("s1"), pendingSample("s2"), pendingSample("s3"))
        advanceUntilIdle()

        job.cancel()

        // Initial emission (1) + two updates (2, 3)
        assertEquals(listOf(1, 2, 3), results)
    }
}

private class FakeCountAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeLocalIdentity(): Flow<LocalIdentity?> =
        flowOf(userId?.let { LocalIdentity(userId = it, email = "user@example.com") })
    override suspend fun currentLocalUserId(): String? = userId
    override suspend fun isAuthenticated(): Boolean = userId != null
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun hasActiveSession(): Boolean = userId != null
    override suspend fun getCurrentUserId(): String? = userId
    override suspend fun signOut() = Unit
}

private class FakeCountSampleRepository(
    private val flagged: MutableStateFlow<List<Sample>> = MutableStateFlow(emptyList()),
) : SampleRepository {
    override suspend fun saveSample(sample: Sample) = Unit
    override fun observeLatestSample(userId: String): Flow<Sample?> = flowOf(null)
    override fun observeAllSamples(userId: String): Flow<List<Sample>> = flowOf(emptyList())
    override suspend fun getSampleById(sampleId: String): Sample? = null
    override fun observeSamplesForSession(
        sessionId: String,
        userId: String,
    ): Flow<List<Sample>> = flowOf(emptyList())
    override suspend fun getSamplesForSession(sessionId: String, userId: String): List<Sample> =
        emptyList()
    override suspend fun getSamplesPendingSync(userId: String): List<Sample> = emptyList()
    override fun observeFlaggedSamplesForSession(
        sessionId: String,
        userId: String,
    ): Flow<List<Sample>> = flagged
}

private fun pendingSample(id: String): Sample = Sample(
    id = id,
    userId = "user-1",
    timestamp = 1_000L,
    verifiedAt = 1_000L,
    deviceId = "device-1",
    sessionId = "session-1",
    filePath = "/tmp/$id.jpg",
    storagePath = null,
    latitude = 10.0,
    longitude = 20.0,
    accuracyMeters = 5f,
    status = SampleStatus.FLAGGED,
)
