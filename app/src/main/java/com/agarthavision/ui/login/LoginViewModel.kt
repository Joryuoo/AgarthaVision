package com.agarthavision.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.usecase.auth.HasActiveSessionUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the email/password login form.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isCheckingSession: Boolean = true,
    val isSubmitting: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
) {
    /**
     * Whether the form can accept a submit tap.
     */
    val canSubmit: Boolean
        get() = !isCheckingSession && !isSubmitting
}

/**
 * One-shot events emitted by [LoginViewModel].
 */
sealed interface LoginEvent {
    /**
     * Navigate to the capture flow after auth succeeds.
     */
    data object NavigateToCapture : LoginEvent

    /**
     * Show a destructive login failure toast.
     */
    data class ShowLoginError(val message: String?) : LoginEvent
}

/**
 * Handles Supabase Auth login and cold-start session bootstrap.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val hasActiveSessionUseCase: HasActiveSessionUseCase,
    private val signInUseCase: SignInUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())

    /**
     * Single source of UI state for [LoginScreen].
     */
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()

    /**
     * Navigation and toast events for [LoginScreen].
     */
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        checkPersistedSession()
    }

    /**
     * Updates the email text field and clears its validation error.
     */
    fun onEmailChanged(email: String) {
        _state.update { it.copy(email = email, emailError = false) }
    }

    /**
     * Updates the password text field and clears its validation error.
     */
    fun onPasswordChanged(password: String) {
        _state.update { it.copy(password = password, passwordError = false) }
    }

    /**
     * Validates the form and attempts Supabase sign-in.
     */
    fun onSubmit() {
        val snapshot = state.value
        if (snapshot.isSubmitting || snapshot.isCheckingSession) return

        val email = snapshot.email.trim()
        val password = snapshot.password
        val emailIsValid = EMAIL_PATTERN.matches(email)
        val passwordIsValid = password.isNotBlank()

        if (!emailIsValid || !passwordIsValid) {
            _state.update {
                it.copy(
                    email = email,
                    emailError = !emailIsValid,
                    passwordError = !passwordIsValid,
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, emailError = false, passwordError = false) }
            signInUseCase(email, password)
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(LoginEvent.NavigateToCapture)
                }
                .onFailure { error ->
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(LoginEvent.ShowLoginError(error.message))
                }
        }
    }

    private fun checkPersistedSession() {
        viewModelScope.launch {
            val hasSession = runCatching { hasActiveSessionUseCase() }.getOrDefault(false)
            _state.update { it.copy(isCheckingSession = false) }
            if (hasSession) {
                _events.emit(LoginEvent.NavigateToCapture)
            }
        }
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    }
}
