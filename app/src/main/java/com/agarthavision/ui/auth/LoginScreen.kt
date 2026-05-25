package com.agarthavision.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komoui.components.Button
import com.komoui.components.Input
import com.komoui.themes.styles // extension property helper for KomoTheme tokens

@Composable
fun LoginScreen(
    onNavigateToCapture: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Listen for successful authentication state change to route away
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onNavigateToCapture()
        }
    }

    LoginScreenContent(
        state = state,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onLoginClick = viewModel::login
    )
}

@Composable
private fun LoginScreenContent(
    state: LoginState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.styles.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginHeader()

            LoginInputs(
                state = state,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange
            )

            LoginErrorText(error = state.error)

            LoginSubmitButton(
                isLoading = state.isLoading,
                onClick = onLoginClick
            )
        }
    }
}

// --- Extracted Composables ---

@Composable
private fun LoginHeader() {
    Text(
        text = "AgarthaVision",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.styles.foreground
    )
    Text(
        text = "Parasite Surveillance System",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.styles.mutedForeground
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun LoginInputs(
    state: LoginState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    Input(
        value = state.email,
        onValueChange = onEmailChange,
        placeholder = "Medical Technologist Email",
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Input(
        value = state.password,
        onValueChange = onPasswordChange,
        placeholder = "Password",
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.LoginErrorText(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.styles.destructive,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LoginSubmitButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.styles.primaryForeground,
                modifier = Modifier.height(24.dp)
            )
        } else {
            Text(text = "Log In")
        }
    }
}
