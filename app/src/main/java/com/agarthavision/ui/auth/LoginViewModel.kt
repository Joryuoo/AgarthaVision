package com.agarthavision.ui.auth

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChanged(email: String) {
        _state.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _state.update { it.copy(password = password, error = null) }
    }

    fun login() {
        val currentEmail = _state.value.email.trim()
        val currentPassword = _state.value.password

        if (currentEmail.isEmpty() || currentPassword.isEmpty()) {
            _state.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                supabaseClient.auth.signInWith(Email) {
                    email = currentEmail
                    password = currentPassword
                }
                _state.update { it.copy(isLoading = false, isAuthenticated = true) }
            } catch (e: RestException) {
                // Supabase API rejected the request (e.g., wrong password or user not found)
                _state.update {
                    it.copy(isLoading = false, error = e.error ?: "Invalid email or password.")
                }
            } catch (e: HttpRequestTimeoutException) {
                // Ktor network timeout
                _state.update {
                    it.copy(isLoading = false, error = "Connection timed out. Please check your network.")
                }
            } catch (e: Exception) {
                // Standard coroutine practice: never swallow a CancellationException
                if (e is CancellationException) throw e

                // Suppress the linter for the generic fallback
                @Suppress("TooGenericExceptionCaught")
                _state.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "An unexpected error occurred.")
                }
            }
        }
    }
}
