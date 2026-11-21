package com.agarthavision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agarthavision.domain.model.LaboratoryBranding
import com.agarthavision.domain.model.LocalIdentity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Stores one device identity and its laboratory together, so no other account sees its branding. */
class LocalIdentityCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    fun observeIdentity(): Flow<LocalIdentity?> = dataStore.data.map { preferences ->
        val userId = preferences[USER_ID] ?: return@map null
        val displayName = preferences[DISPLAY_NAME]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: LocalIdentity.PENDING_PROFILE_PLACEHOLDER
        LocalIdentity(
            userId = userId,
            email = preferences[EMAIL].orEmpty(),
            displayName = displayName,
            laboratory = preferences[LABORATORY_NAME]?.let { name ->
                LaboratoryBranding(name, preferences[LABORATORY_LOGO])
            },
        )
    }

    suspend fun currentUserId(): String? = dataStore.data.first()[USER_ID]

    suspend fun saveIdentity(userId: String, email: String, displayName: String?) {
        dataStore.edit { preferences ->
            if (preferences[USER_ID] != userId) {
                preferences.remove(LABORATORY_NAME)
                preferences.remove(LABORATORY_LOGO)
            }
            preferences[USER_ID] = userId
            preferences[EMAIL] = email
            if (displayName.isNullOrBlank()) {
                preferences.remove(DISPLAY_NAME)
            } else {
                preferences[DISPLAY_NAME] = displayName
            }
        }
    }

    suspend fun saveLaboratory(
        userId: String,
        laboratory: LaboratoryBranding?,
        stillSignedIn: () -> Boolean,
    ) {
        dataStore.edit { preferences ->
            if (preferences[USER_ID] != userId || !stillSignedIn()) return@edit
            if (laboratory == null) {
                preferences.remove(LABORATORY_NAME)
            } else {
                preferences[LABORATORY_NAME] = laboratory.name
            }
            val logo = laboratory?.logoPng
            if (logo == null) {
                preferences.remove(LABORATORY_LOGO)
            } else {
                preferences[LABORATORY_LOGO] = logo
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(EMAIL)
            preferences.remove(DISPLAY_NAME)
            preferences.remove(LABORATORY_NAME)
            preferences.remove(LABORATORY_LOGO)
        }
    }

    private companion object {
        val USER_ID = stringPreferencesKey("local_identity_user_id")
        val EMAIL = stringPreferencesKey("local_identity_email")
        val DISPLAY_NAME = stringPreferencesKey("local_identity_display_name")
        val LABORATORY_NAME = stringPreferencesKey("local_identity_laboratory_name")
        val LABORATORY_LOGO = byteArrayPreferencesKey("local_identity_laboratory_logo")
    }
}
