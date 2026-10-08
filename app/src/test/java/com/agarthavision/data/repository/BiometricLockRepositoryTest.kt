package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricLockRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: BiometricLockRepositoryImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmp.newFile("test_biometric_prefs.preferences_pb") },
        )
        repository = BiometricLockRepositoryImpl(dataStore)
    }

    @Test
    fun `isBiometricLockEnabled defaults to false`() = runTest {
        assertFalse(repository.isBiometricLockEnabled.first())
    }

    @Test
    fun `setBiometricLockEnabled updates flow value to true`() = runTest {
        repository.setBiometricLockEnabled(true)
        assertTrue(repository.isBiometricLockEnabled.first())
    }

    @Test
    fun `setBiometricLockEnabled updates flow value back to false`() = runTest {
        repository.setBiometricLockEnabled(true)
        assertTrue(repository.isBiometricLockEnabled.first())

        repository.setBiometricLockEnabled(false)
        assertFalse(repository.isBiometricLockEnabled.first())
    }
}
