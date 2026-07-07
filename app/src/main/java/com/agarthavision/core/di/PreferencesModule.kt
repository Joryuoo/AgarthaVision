package com.agarthavision.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.agarthavision.data.repository.ThemePreferenceRepositoryImpl
import com.agarthavision.domain.repository.ThemePreferenceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "agartha_settings",
)

/**
 * Provides the app-scoped settings DataStore.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}

/**
 * Binds preference repository interfaces to their data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesRepositoryModule {
    @Binds
    abstract fun bindThemePreferenceRepository(
        implementation: ThemePreferenceRepositoryImpl,
    ): ThemePreferenceRepository
}
