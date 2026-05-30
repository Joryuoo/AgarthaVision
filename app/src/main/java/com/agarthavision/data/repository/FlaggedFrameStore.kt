package com.agarthavision.data.repository

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.usecase.capture.DeleteFlaggedSampleUseCase
import com.agarthavision.domain.usecase.capture.PersistFlaggedFrameUseCase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Room-backed store for frames flagged during a recording session.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FlaggedFrameStore @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val sampleDao: SampleDao,
    private val sampleImageStore: SampleImageStore,
    private val persistFlaggedFrameUseCase: PersistFlaggedFrameUseCase,
    private val deleteFlaggedSampleUseCase: DeleteFlaggedSampleUseCase,
    private val gson: Gson,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val predictionListType = object : TypeToken<List<PredictionDto>>() {}.type

    /**
     * Observable stream of flagged frames for the active session.
     */
    val state: StateFlow<List<FlaggedFrame>> = combine(
        authRepository.userIdFlow,
        sessionManager.state,
    ) { userId, sessionState ->
        userId to sessionState
    }
        .flatMapLatest { (userId, sessionState) ->
            val sessionId = (sessionState as? SessionState.Active)?.session?.sessionId
            if (userId == null || sessionId == null) {
                flowOf(emptyList())
            } else {
                sampleDao.observeFlaggedSamplesForSession(sessionId, userId)
                    .map { entities -> entities.map { it.toFlaggedFrame() } }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(frame: FlaggedFrame) {
        persistFlaggedFrameUseCase(frame)
    }

    suspend fun remove(frame: FlaggedFrame) {
        if (frame.sampleId.isNotBlank()) {
            deleteFlaggedSampleUseCase(frame.sampleId)
        }
    }

    suspend fun toggleRepeat(frame: FlaggedFrame) {
        if (frame.sampleId.isNotBlank()) {
            sampleDao.toggleIsRepeat(frame.sampleId)
        }
    }

    suspend fun clear() {
        val userId = authRepository.getCurrentUserId() ?: return
        val sessionId = (sessionManager.state.value as? SessionState.Active)?.session?.sessionId
            ?: return
        val samples = sampleDao.getFlaggedSamplesForSession(sessionId, userId)
        samples.forEach { sampleImageStore.deleteJpeg(it.imagePath) }
        sampleDao.deleteFlaggedSamplesForSession(sessionId, userId)
    }

    private fun SampleEntity.toFlaggedFrame(): FlaggedFrame {
        val predictions = predictionsJson
            ?.let { gson.fromJson<List<PredictionDto>>(it, predictionListType) }
            .orEmpty()
        val jpegBytes = runCatching { File(imagePath).readBytes() }.getOrDefault(ByteArray(0))

        return FlaggedFrame(
            sampleId = sampleId,
            sessionId = sessionId,
            capturedAt = Instant.ofEpochMilli(timestamp),
            jpegBytes = jpegBytes,
            predictions = predictions,
            source = if (isManual) FrameSource.MANUAL else FrameSource.MODEL,
            markedAsRepeat = isRepeat,
            inferenceModelVersion = inferenceModelVersion,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }
}
