package com.agarthavision.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
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
    val isOffline: Boolean = false,
    val isSubmitting: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
) {
    /**
     * Whether the form can accept a submit tap: not mid-submit and online (login always
     * requires connectivity, per ADR-007).
     */
    val canSubmit: Boolean
        get() = !isSubmitting && !isOffline
}

/**
 * One-shot events emitted by [LoginViewModel].
 */
sealed interface LoginEvent {
    /**
     * Navigate back to the Dashboard after auth (and the claim + sync pass) succeeds.
     */
    data object NavigateBack : LoginEvent

    /**
     * Show a destructive login failure toast.
     */
    data class ShowLoginError(val message: String?) : LoginEvent
}

/**
 * Handles Supabase Auth login. Per ADR-007 there is no cold-start auto-forward; login is
 * an explicit action gated on connectivity, and on success it claims unowned local data
 * for the account and triggers a pending-sync pass before returning to the Dashboard.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val claimLocalDataUseCase: ClaimLocalDataUseCase,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(isOffline = !connectivityObserver.currentlyOnline()))

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
        observeConnectivity()
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
     * Validates the form and attempts Supabase sign-in when online.
     */
    fun onSubmit() {
        val snapshot = state.value
        if (!snapshot.canSubmit) return

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
                    claimAndSync()
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(LoginEvent.NavigateBack)
                }
                .onFailure { error ->
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(LoginEvent.ShowLoginError(error.message))
                }
        }
    }

    /** Silently claims unowned local data for the account, then pushes pending rows. */
    private suspend fun claimAndSync() {
        val userId = authRepository.currentLocalUserId() ?: return
        claimLocalDataUseCase(userId)
        syncPendingDataUseCase()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _state.update { it.copy(isOffline = !online) }
            }
        }
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    }
}
