@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "UnusedPrivateMember")

package com.agarthavision.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        containerColor = Color.Transparent,
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF0F4FF),
                            Color(0xFFE8EFFF)
                        )
                    )
                )
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp), // Adjust padding for inner content
        ) {
            if (state.isCheckingSession) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                    color = MaterialTheme.styles.primary,
                    trackColor = MaterialTheme.styles.muted,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp), // Padding from top for the brand row
                ) {
                    AgarthaBrandHeader()
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    LoginForm(
                        state = state,
                        actions = actions,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    SecurityNoteBanner(
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgarthaBrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logo Placeholder matching image
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2563EB)), // Adjusted brand blue
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(2.dp, Color.White, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.Center)
                )
            }
        }
        
        Column {
            Text(
                text = "AgarthaVision",
                color = Color(0xFF0F172A), // Ink
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "PARASITOLOGY · MOBILE",
                color = Color(0xFF6E6E73), // Muted
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SecurityNoteBanner(modifier: Modifier = Modifier) {
    val securityNote = stringResource(R.string.login_security_note)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEEF2FF)) // Brand tint
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = Color(0xFF1E40AF), // Brand
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = securityNote,
            color = Color(0xFF1E40AF), // Brand
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
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
    val forgotPassword = stringResource(R.string.login_forgot_password)
    
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = Color(0xFF0F172A),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = subtitle,
                color = Color(0xFF6E6E73),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

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
            forgotText = forgotPassword,
            value = state.password,
            onValueChange = actions.onPasswordChanged,
            placeholder = passwordPlaceholder,
            passwordVisible = passwordVisible,
            onPasswordVisibleChanged = { passwordVisible = it },
            inputOptions = LoginInputOptions(
                enabled = state.canSubmit,
                isError = state.passwordError,
                errorText = passwordError,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            ),
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = actions.onSubmit,
            size = ButtonSize.Lg,
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            fullWidth = false,
            modifier = Modifier.widthIn(min = 120.dp)
        ) {
            Text(text = submitLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = Color(0xFF6E6E73),
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

@Composable
private fun PasswordInput(
    label: String,
    forgotText: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean,
    onPasswordVisibleChanged: (Boolean) -> Unit,
    inputOptions: LoginInputOptions,
) {
    LoginInputGroup(
        label = label,
        forgotText = forgotText,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        inputOptions = inputOptions,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color(0xFF6E6E73),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            IconButton(onClick = { onPasswordVisibleChanged(!passwordVisible) }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = Color(0xFF6E6E73),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
    forgotText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (forgotText != null) {
                Text(
                    text = forgotText,
                    color = Color(0xFF2563EB), // Brand blue
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { /* Handle forgot password */ }
                )
            }
        }
        
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
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
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
