package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferenceRepositoryImplTest {

    private val fakeDataStore = FakePreferencesDataStore()
    private val repository = ThemePreferenceRepositoryImpl(fakeDataStore)

    @Test
    fun `themeMode defaults to LIGHT when unset`() = runTest {
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    @Test
    fun `themeMode defaults to LIGHT when the stored value is unrecognized`() = runTest {
        fakeDataStore.state.value = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "SEPIA",
        )

        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    @Test
    fun `setThemeMode persists the mode and themeMode reflects it`() = runTest {
        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }
}

/** Minimal in-memory [DataStore] fake — mirrors this repo's fake-DAO test convention. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.map { it }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
