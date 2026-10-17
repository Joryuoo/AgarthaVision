package com.agarthavision.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingPreferenceRepository {
    val hasSeenOnboarding: Flow<Boolean>
    suspend fun setHasSeenOnboarding(hasSeen: Boolean)
}
