package com.agarthavision.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.PasswordChangeResult
import com.agarthavision.domain.usecase.auth.ChangePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why one password field is marked. The screen picks the words. */
enum class PasswordFieldError { REQUIRED, WRONG_CURRENT, MISMATCH, SAME_AS_CURRENT, WEAK }

/** Why the whole change failed when no field is at fault. */
enum class ChangePasswordFailure { NO_CONNECTION, FAILED }

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isOffline: Boolean = false,
    val isSubmitting: Boolean = false,
    val currentError: PasswordFieldError? = null,
    val newError: PasswordFieldError? = null,
    val confirmError: PasswordFieldError? = null,
    /** The provider's own words on why the new password is too weak, when it gave any. */
    val weakPasswordDetail: String? = null,
    val failure: ChangePasswordFailure? = null,
    val isChanged: Boolean = false,
) {
    /** Online only (14zcqntjph9): the button stays disabled offline and the screen says why. */
    val canSubmit: Boolean
        get() = !isSubmitting && !isOffline && !isChanged
}

/**
 * Backs the Change password screen reached from Settings (14zcqntjph9): current password, new
 * password, confirm.
 *
 * Fields are checked here first so an empty or mistyped form never reaches the server. What
 * only the server can judge — the current password, the provider's strength rules — comes back
 * from [ChangePasswordUseCase] and lands on the field it concerns.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChangePasswordUiState(isOffline = !connectivityObserver.currentlyOnline()),
    )
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _state.update { it.copy(isOffline = !online) }
            }
        }
    }

    fun onCurrentPasswordChanged(value: String) {
        _state.update { it.copy(currentPassword = value, currentError = null, failure = null) }
    }

    fun onNewPasswordChanged(value: String) {
        // A new password makes the confirmation's verdict stale too.
        _state.update {
            it.copy(
                newPassword = value,
                newError = null,
                weakPasswordDetail = null,
                confirmError = null,
                failure = null,
            )
        }
    }

    fun onConfirmPasswordChanged(value: String) {
        _state.update { it.copy(confirmPassword = value, confirmError = null, failure = null) }
    }

    fun onSubmit() {
        val form = state.value
        if (!form.canSubmit) return

        val checked = form.withFieldErrors()
        if (checked.currentError != null || checked.newError != null || checked.confirmError != null) {
            _state.value = checked
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, failure = null) }
            val result = changePasswordUseCase(form.currentPassword, form.newPassword)
            _state.update { it.copy(isSubmitting = false).withResult(result) }
        }
    }

    private fun ChangePasswordUiState.withFieldErrors(): ChangePasswordUiState = copy(
        currentError = PasswordFieldError.REQUIRED.takeIf { currentPassword.isEmpty() },
        newError = when {
            newPassword.isEmpty() -> PasswordFieldError.REQUIRED
            newPassword == currentPassword -> PasswordFieldError.SAME_AS_CURRENT
            else -> null
        },
        confirmError = when {
            confirmPassword.isEmpty() -> PasswordFieldError.REQUIRED
            confirmPassword != newPassword -> PasswordFieldError.MISMATCH
            else -> null
        },
    )

    private fun ChangePasswordUiState.withResult(result: PasswordChangeResult): ChangePasswordUiState =
        when (result) {
            // The passwords are dropped from memory the moment they are no longer needed.
            PasswordChangeResult.Changed -> ChangePasswordUiState(isOffline = isOffline, isChanged = true)
            PasswordChangeResult.WrongCurrentPassword -> copy(currentError = PasswordFieldError.WRONG_CURRENT)
            is PasswordChangeResult.WeakPassword ->
                copy(newError = PasswordFieldError.WEAK, weakPasswordDetail = result.detail)
            PasswordChangeResult.SamePassword -> copy(newError = PasswordFieldError.SAME_AS_CURRENT)
            PasswordChangeResult.NoConnection -> copy(failure = ChangePasswordFailure.NO_CONNECTION)
            PasswordChangeResult.Failed -> copy(failure = ChangePasswordFailure.FAILED)
        }
}
