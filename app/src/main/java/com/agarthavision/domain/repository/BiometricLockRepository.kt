package com.agarthavision.domain.repository

import kotlinx.coroutines.flow.Flow

interface BiometricLockRepository {
    val isBiometricLockEnabled: Flow<Boolean>
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
