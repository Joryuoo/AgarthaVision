package com.agarthavision.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.agarthavision.ui.components.AgarthaToastHost
import com.agarthavision.ui.components.AgarthaToastState
import com.agarthavision.ui.components.AgarthaToastVariant
import com.agarthavision.ui.components.rememberAgarthaToastState
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.agarthavision.ui.theme.AppColors

/**
 * Login route for dashboard-provisioned Supabase accounts.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberAgarthaToastState()
    val loginFailedTitle = stringResource(R.string.login_failed_title)
    val loginFailedGeneric = stringResource(R.string.login_failed_generic)

    LaunchedEffect(viewModel, toastState) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateToCapture -> onLoggedIn()
                is LoginEvent.ShowLoginError -> {
                    toastState.show(
                        message = "$loginFailedTitle\n${event.message ?: loginFailedGeneric}",
                        variant = AgarthaToastVariant.Destructive,
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
        toastState = toastState,
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
    toastState: AgarthaToastState,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        snackbarHost = {
            AgarthaToastHost(
                state = toastState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.isCheckingSession) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    color = colors.accent,
                    trackColor = colors.borderStrong,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = 480.dp)
                        .padding(top = 72.dp, start = 28.dp, end = 28.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppMark()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "AgarthaVision",
                                color = colors.accent,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.75).sp, // -0.025em * 30px
                                lineHeight = 33.sp, // 1.1
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sign in to continue your clinical work.",
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 21.75.sp, // 1.45
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))

                        LoginForm(state = state, actions = actions)
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { /* Handle forgot password */ }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMark() {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .size(80.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = AppColors.Gray900.copy(alpha = 0.22f),
                ambientColor = AppColors.Gray900.copy(alpha = 0.06f)
            )
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_logo),
            contentDescription = "AgarthaVision Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    actions: LoginActions,
    modifier: Modifier = Modifier,
) {
    val emailErrorText = stringResource(R.string.login_email_error)
    val passwordErrorText = stringResource(R.string.login_password_error)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        LoginInputGroup(
            label = "Email",
            value = state.email,
            onValueChange = actions.onEmailChanged,
            placeholder = "you@hospital.org",
            isError = state.emailError,
            errorText = emailErrorText,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        LoginInputGroup(
            label = "Password",
            value = state.password,
            onValueChange = actions.onPasswordChanged,
            placeholder = "••••••••••",
            isError = state.passwordError,
            errorText = passwordErrorText,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { actions.onSubmit() })
        )

        Spacer(modifier = Modifier.height(22.dp))

        val themeColors = AgarthaTheme.colors
        Button(
            onClick = actions.onSubmit,
            enabled = state.canSubmit,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = themeColors.accent,
                contentColor = themeColors.onAccent,
                disabledContainerColor = themeColors.accent.copy(alpha = 0.5f),
                disabledContentColor = themeColors.onAccent.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = themeColors.onAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Sign in",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun LoginInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
    errorText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isError) colors.danger else if (isFocused) colors.accent else colors.borderStrong

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            ),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isError) colors.dangerTint else colors.surface, RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textTertiary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                color = colors.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

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
            toastState = rememberAgarthaToastState(),
        )
    }
}
