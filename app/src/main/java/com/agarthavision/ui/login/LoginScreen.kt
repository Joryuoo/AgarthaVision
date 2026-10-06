package com.agarthavision.ui.login

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightBrush
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.SignedOutNotice
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

    // The activity defaults to adjustPan, which would pan the window up on top of imePadding() and
    // leave a blank band above the keyboard. Resize while on Login, then restore for other screens.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val previous = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { previous?.let { window.setSoftInputMode(it) } }
    }

    LaunchedEffect(viewModel, toastState) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateBack -> onLoggedIn()
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

internal data class LoginActions(
    val onEmailChanged: (String) -> Unit,
    val onPasswordChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
)

private val HERO_HEIGHT = 300.dp
private val SHEET_OVERLAP = 28.dp

@Composable
internal fun LoginScreenContent(
    state: LoginUiState,
    actions: LoginActions,
    toastState: AgarthaToastState,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        // The hero paints under the status bar, so insets are applied by hand below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            AgarthaToastHost(
                state = toastState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(padding)
                .imePadding(),
        ) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val heroHeight = HERO_HEIGHT + statusBarTop
            val sheetMinHeight = (maxHeight - heroHeight + SHEET_OVERLAP).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginHero(height = heroHeight, topInset = statusBarTop)
                LoginSheet(
                    state = state,
                    actions = actions,
                    minHeight = sheetMinHeight,
                )
            }
        }
    }
}

@Composable
private fun LoginHero(height: Dp, topInset: Dp) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(if (colors.isDark) AppColors.MaroonPressed else AppColors.Maroon),
    ) {
        // Outline lung pattern from the brand artwork.
        Image(
            painter = painterResource(id = R.drawable.ill_login_pattern),
            contentDescription = null,
            alpha = 0.10f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 150.dp, y = (-70).dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = 500.dp, height = 537.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.login_tagline),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoginSheet(
    state: LoginUiState,
    actions: LoginActions,
    minHeight: Dp,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier
            // Pull the sheet up so it overlaps the hero's bottom edge.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = SHEET_OVERLAP.roundToPx()
                layout(placeable.width, placeable.height - overlap) {
                    placeable.place(0, -overlap)
                }
            }
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(colors.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            // IME inset already includes the nav-bar area, so exclude it to avoid double-counting.
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
            .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.login_title),
                color = colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                lineHeight = 36.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                color = colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))

            state.signedOutNotice?.let { notice ->
                SignedOutNoticeCard(notice)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isOffline) {
                OfflineNotice()
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoginForm(state = state, actions = actions)
        }

        Text(
            text = stringResource(R.string.login_security_note),
            color = colors.textTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

/**
 * Why the phone is back at the login screen when the medtech did not sign out (14zcqntjph8).
 * Stays until the next successful sign-in, so it is read even if the wipe ran in the background.
 */
@Composable
private fun SignedOutNoticeCard(notice: SignedOutNotice) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LOGIN_SIGNED_OUT_NOTICE_TAG)
            .background(colors.warningTint, RoundedCornerShape(12.dp))
            .border(1.dp, colors.warning, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.login_signed_out_by_server_title),
            color = colors.warningText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.login_signed_out_by_server_body),
            color = colors.warningText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (notice.unsyncedKept > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.login_signed_out_unsynced_kept,
                    notice.unsyncedKept,
                    notice.unsyncedKept,
                ),
                color = colors.warningText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            )
        }
    }
}

/** Test tag on [SignedOutNoticeCard]. */
const val LOGIN_SIGNED_OUT_NOTICE_TAG = "login_signed_out_notice"

@Composable
private fun OfflineNotice() {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningTint, RoundedCornerShape(12.dp))
            .border(1.dp, colors.warning, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.login_offline_notice),
            color = colors.warningText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.25f),
                ambientColor = Color.Black.copy(alpha = 0.25f),
            )
            .background(Color.White, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Maroon lungs with a dark eye on transparent, so it reads on the white tile in both themes.
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = stringResource(R.string.login_logo_description),
            modifier = Modifier.fillMaxSize(),
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
    var passwordVisible by remember { mutableStateOf(false) }
    val themeColors = AgarthaTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        LoginInputGroup(
            label = stringResource(R.string.login_email_label),
            value = state.email,
            onValueChange = actions.onEmailChanged,
            placeholder = stringResource(R.string.login_email_placeholder),
            config = LoginFieldConfig(
                leadingIcon = Icons.Outlined.Mail,
                isError = state.emailError,
                errorText = emailErrorText,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LoginInputGroup(
            label = stringResource(R.string.login_password_label),
            value = state.password,
            onValueChange = actions.onPasswordChanged,
            placeholder = stringResource(R.string.login_password_placeholder),
            config = LoginFieldConfig(
                leadingIcon = Icons.Outlined.Lock,
                isError = state.passwordError,
                errorText = passwordErrorText,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
                trailing = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                            ),
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = stringResource(R.string.login_forgot_password_link),
                color = themeColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { /* Handle forgot password */ }
                    .padding(vertical = 2.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = actions.onSubmit,
            enabled = state.canSubmit,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = themeColors.brandFill,
                contentColor = themeColors.onBrandFill,
                disabledContainerColor = themeColors.brandFill.copy(alpha = 0.5f),
                disabledContentColor = themeColors.onBrandFill.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = themeColors.onAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.login_submit),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        state.stage?.let { stage ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(
                    when (stage) {
                        LoginStage.SIGNING_IN -> R.string.login_stage_signing_in
                        LoginStage.DOWNLOADING_PATIENTS -> R.string.login_stage_downloading_patients
                    },
                ),
                color = themeColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Field-level config for [LoginInputGroup] — icon, validation and keyboard behavior, kept separate from
 * the always-required per-field identity/state params (label, value, onValueChange, placeholder).
 */
private data class LoginFieldConfig(
    val leadingIcon: ImageVector? = null,
    val isError: Boolean = false,
    val errorText: String? = null,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val keyboardActions: KeyboardActions = KeyboardActions.Default,
    val trailing: (@Composable () -> Unit)? = null,
)

@Composable
private fun LoginInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    config: LoginFieldConfig = LoginFieldConfig(),
) {
    val colors = AgarthaTheme.colors
    val leadingIcon = config.leadingIcon
    val isError = config.isError
    val errorText = config.errorText
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isError) colors.danger else if (isFocused) colors.accent else colors.borderStrong
        val shape = RoundedCornerShape(14.dp)
        val fieldShape = RoundedCornerShape(10.dp)

        CompositionLocalProvider(LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(
                color = colors.textPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal
            ),
            singleLine = true,
            visualTransformation = config.visualTransformation,
            keyboardOptions = config.keyboardOptions,
            keyboardActions = config.keyboardActions,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isError) colors.dangerTint else colors.surfaceVariant, shape)
                        .border(width = 1.5.dp, color = borderColor, shape = shape)
                        .padding(start = 16.dp, end = if (config.trailing != null) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.textTertiary,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        innerTextField()
                    }
                    config.trailing?.invoke()
                }
            }
        )
        }
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
            state = LoginUiState(),
            actions = LoginActions(
                onEmailChanged = {},
                onPasswordChanged = {},
                onSubmit = {},
            ),
            toastState = rememberAgarthaToastState(),
        )
    }
}
