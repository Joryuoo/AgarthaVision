@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "UnusedPrivateMember")

package com.agarthavision.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaSpacing
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.komoui.components.Button
import com.komoui.components.ButtonSize
import com.komoui.components.Input
import com.komoui.components.sooner.SonnerEvent
import com.komoui.components.sooner.SonnerHost
import com.komoui.components.sooner.SonnerVariant
import com.komoui.components.sooner.showSonner
import com.komoui.themes.styles

/**
 * Login route for dashboard-provisioned Supabase accounts.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val loginFailedTitle = stringResource(R.string.login_failed_title)
    val loginFailedGeneric = stringResource(R.string.login_failed_generic)

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateToCapture -> onLoggedIn()
                is LoginEvent.ShowLoginError -> {
                    snackbarHostState.showSonner(
                        SonnerEvent(
                            message = loginFailedTitle,
                            subMessage = event.message ?: loginFailedGeneric,
                            withDismissAction = true,
                            variant = SonnerVariant.Destructive,
                        ),
                    )
                }
            }
        }
    }

    LoginScreenContent(
        state = state,
        actions = LoginActions(
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onSubmit = viewModel::onSubmit,
        ),
        snackbarHostState = snackbarHostState,
    )
}

private data class LoginActions(
    val onEmailChanged: (String) -> Unit,
    val onPasswordChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
)

@Composable
private fun LoginScreenContent(
    state: LoginUiState,
    actions: LoginActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.styles.background,
        snackbarHost = {
            SonnerHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AgarthaSpacing.screenEdge),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(AgarthaSpacing.screenEdge),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isCheckingSession) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.styles.primary,
                    trackColor = MaterialTheme.styles.muted,
                )
            } else {
                LoginForm(
                    state = state,
                    actions = actions,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    actions: LoginActions,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.login_title)
    val subtitle = stringResource(R.string.login_subtitle)
    val emailLabel = stringResource(R.string.login_email_label)
    val emailPlaceholder = stringResource(R.string.login_email_placeholder)
    val emailError = stringResource(R.string.login_email_error)
    val passwordLabel = stringResource(R.string.login_password_label)
    val passwordPlaceholder = stringResource(R.string.login_password_placeholder)
    val passwordError = stringResource(R.string.login_password_error)
    val submitLabel = stringResource(R.string.login_submit)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs)) {
            Text(
                text = title,
                color = MaterialTheme.styles.foreground,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.styles.mutedForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(AgarthaSpacing.xs))

        EmailInput(
            label = emailLabel,
            value = state.email,
            onValueChange = actions.onEmailChanged,
            placeholder = emailPlaceholder,
            inputOptions = LoginInputOptions(
                enabled = state.canSubmit,
                isError = state.emailError,
                errorText = emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            ),
        )

        PasswordInput(
            label = passwordLabel,
            value = state.password,
            onValueChange = actions.onPasswordChanged,
            placeholder = passwordPlaceholder,
            inputOptions = LoginInputOptions(
                enabled = state.canSubmit,
                isError = state.passwordError,
                errorText = passwordError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            ),
        )

        Button(
            onClick = actions.onSubmit,
            size = ButtonSize.Lg,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            fullWidth = true,
        ) {
            Text(text = submitLabel)
        }
    }
}

@Composable
private fun EmailInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputOptions: LoginInputOptions,
) {
    LoginInputGroup(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        inputOptions = inputOptions,
    )
}

@Composable
private fun PasswordInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputOptions: LoginInputOptions,
) {
    LoginInputGroup(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        inputOptions = inputOptions,
    )
}

@Composable
private fun LoginInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputOptions: LoginInputOptions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs),
    ) {
        Text(
            text = label,
            color = MaterialTheme.styles.foreground,
            style = MaterialTheme.typography.labelLarge,
        )
        Input(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = inputOptions.enabled,
            singleLine = true,
            isError = inputOptions.isError,
            visualTransformation = inputOptions.visualTransformation,
            keyboardOptions = inputOptions.keyboardOptions,
            keyboardActions = inputOptions.keyboardActions,
            supportingText = {
                if (inputOptions.isError) {
                    Text(text = inputOptions.errorText)
                }
            },
        )
    }
}

private data class LoginInputOptions(
    val enabled: Boolean,
    val isError: Boolean,
    val errorText: String,
    val keyboardOptions: KeyboardOptions,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
    val keyboardActions: KeyboardActions = KeyboardActions.Default,
)

@Preview(showBackground = true)
@Composable
private fun LoginScreenContentPreview() {
    AgarthaVisionTheme {
        LoginScreenContent(
            state = LoginUiState(isCheckingSession = false),
            actions = LoginActions(
                onEmailChanged = {},
                onPasswordChanged = {},
                onSubmit = {},
            ),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
