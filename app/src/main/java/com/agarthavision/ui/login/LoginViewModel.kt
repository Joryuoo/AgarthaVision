package com.agarthavision.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.auth.BiometricPromptManager
import com.agarthavision.core.auth.BiometricStatus
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.BiometricLockRepository
import com.agarthavision.domain.usecase.auth.CompleteSignInUseCase
import com.agarthavision.domain.usecase.auth.ObserveSignedOutNoticeUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val isBiometricAvailable: Boolean = false,
    val isBiometricLockEnabled: Boolean = false,
    /** Set when the server signed this phone out (14zcqntjph8); the screen says why. */
    val signedOutNotice: SignedOutNotice? = null,
    /** What the submit is doing right now, for the progress text under the button. */
    val stage: LoginStage? = null,
) {
    /**
     * Whether the form can accept a submit tap: not mid-submit and online (login always
     * requires connectivity, per ADR-007).
     */
    val canSubmit: Boolean
        get() = !isSubmitting && !isOffline
}

/**
 * The step a submit is on, shown under the button so a slow sign-in reads as progress.
 */
enum class LoginStage { SIGNING_IN, DOWNLOADING_PATIENTS }

/**
 * One-shot events emitted by [LoginViewModel].
 */
sealed interface LoginEvent {
    /**
     * Navigate to the Dashboard once sign-in succeeded and the patient list has been pulled (or
     * the pull failed; it never blocks).
     */
    data object NavigateBack : LoginEvent

    /**
     * Show a destructive login failure toast.
     */
    data class ShowLoginError(val message: String?) : LoginEvent
}

/**
 * Handles Supabase Auth login. Per ADR-007 there is no cold-start auto-forward; login is
 * an explicit action gated on connectivity. On success it waits only for the patient list
 * ([CompleteSignInUseCase]); the rest of the sync runs in the background.
 */
@Suppress("LongParameterList")
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val completeSignInUseCase: CompleteSignInUseCase,
    private val observeSignedOutNoticeUseCase: ObserveSignedOutNoticeUseCase,
    private val biometricPromptManager: BiometricPromptManager,
    private val biometricLockRepository: BiometricLockRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            isOffline = !connectivityObserver.currentlyOnline(),
            isBiometricAvailable = biometricPromptManager.getBiometricStatus() == BiometricStatus.READY,
        )
    )

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
        observeSignedOutNotice()
        observeBiometricPreference()
    }

    private fun observeBiometricPreference() {
        viewModelScope.launch {
            biometricLockRepository.isBiometricLockEnabled.collect { enabled ->
                _state.update { it.copy(isBiometricLockEnabled = enabled) }
            }
        }
    }

    fun onBiometricSignInSuccess() {
        viewModelScope.launch {
            _events.emit(LoginEvent.NavigateBack)
        }
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
        val emailIsBlank = email.isBlank()
        val passwordIsBlank = password.isBlank()
        val emailIsValid = !emailIsBlank && EMAIL_PATTERN.matches(email)
        val passwordIsValid = !passwordIsBlank

        if (!emailIsValid || !passwordIsValid) {
            val errorMessage = when {
                emailIsBlank && passwordIsBlank -> "Email and password are required."
                emailIsBlank -> "Email is required."
                !emailIsValid -> "Enter a valid email address."
                else -> "Password is required."
            }
            _state.update {
                it.copy(
                    email = email,
                    emailError = !emailIsValid,
                    passwordError = !passwordIsValid,
                )
            }
            viewModelScope.launch {
                _events.emit(LoginEvent.ShowLoginError(errorMessage))
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmitting = true,
                    emailError = false,
                    passwordError = false,
                    stage = LoginStage.SIGNING_IN,
                )
            }
            signInUseCase(email, password)
                .onSuccess {
                    _state.update { it.copy(stage = LoginStage.DOWNLOADING_PATIENTS) }
                    // Never trap the user on Login: a failed or skipped pull still navigates, and
                    // the background pass requested by the use case catches up.
                    try {
                        completeSignInUseCase().onFailure { error ->
                            android.util.Log.e(TAG, "Post-login patient pull failed", error)
                        }
                    } catch (e: CancellationException) {
                        _state.update { it.copy(isSubmitting = false, stage = null) }
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                        android.util.Log.e(TAG, "Post-login completion threw", e)
                    }
                    _state.update { it.copy(isSubmitting = false, stage = null) }
                    _events.emit(LoginEvent.NavigateBack)
                }
                .onFailure { error ->
                    _state.update { it.copy(isSubmitting = false, stage = null) }
                    _events.emit(LoginEvent.ShowLoginError(error.message))
                }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _state.update { it.copy(isOffline = !online) }
            }
        }
    }

    private fun observeSignedOutNotice() {
        viewModelScope.launch {
            observeSignedOutNoticeUseCase().collect { notice ->
                _state.update { it.copy(signedOutNotice = notice) }
            }
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    }
}
