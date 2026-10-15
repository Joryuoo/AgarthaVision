package com.agarthavision.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.HumanCheckConfig
import com.agarthavision.domain.model.HumanCheckRejectedException
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.usecase.auth.CompleteSignInUseCase
import com.agarthavision.domain.usecase.auth.ObserveSignedOutNoticeUseCase
import com.agarthavision.domain.usecase.auth.SignInUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.ui.components.HumanCheckUiState
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
    /** Set when the server signed this phone out (14zcqntjph8); the screen says why. */
    val signedOutNotice: SignedOutNotice? = null,
    /** What the submit is doing right now, for the progress text under the button. */
    val stage: LoginStage? = null,
    /** The human check every sign-in passes (14zcqntnqxf). */
    val humanCheck: HumanCheckUiState = HumanCheckUiState(),
) {
    /**
     * Whether the form can accept a submit tap: not mid-submit, online (login always requires
     * connectivity, per ADR-007), and the human check passed when the build has one.
     */
    val canSubmit: Boolean
        get() = !isSubmitting && !isOffline && humanCheck.isSatisfied
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
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val completeSignInUseCase: CompleteSignInUseCase,
    private val observeSignedOutNoticeUseCase: ObserveSignedOutNoticeUseCase,
    humanCheckConfig: HumanCheckConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            isOffline = !connectivityObserver.currentlyOnline(),
            humanCheck = HumanCheckUiState(config = humanCheckConfig),
        ),
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

    /** The human check passed; its token goes with the next sign-in. */
    fun onHumanCheckToken(token: String) {
        _state.update { it.copy(humanCheck = it.humanCheck.withToken(token)) }
    }

    /** The token expired unused; the widget fetches a new one by itself. */
    fun onHumanCheckExpired() {
        _state.update { it.copy(humanCheck = it.humanCheck.expired()) }
    }

    /** The widget could not load or the check failed. */
    fun onHumanCheckError() {
        _state.update { it.copy(humanCheck = it.humanCheck.failed()) }
    }

    /** The medtech asked to run the check again. */
    fun onHumanCheckRetry() {
        _state.update { it.copy(humanCheck = it.humanCheck.renewed()) }
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
            signInUseCase(email, password, snapshot.humanCheck.token)
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
                    // A token is spent by any attempt, so the next one needs a fresh check.
                    _state.update {
                        it.copy(isSubmitting = false, stage = null, humanCheck = it.humanCheck.renewed())
                    }
                    val message = if (error is HumanCheckRejectedException) HUMAN_CHECK_REJECTED else error.message
                    _events.emit(LoginEvent.ShowLoginError(message))
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
        const val HUMAN_CHECK_REJECTED =
            "The security check expired or did not pass. Wait for it to finish, then sign in again."
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    }
}
