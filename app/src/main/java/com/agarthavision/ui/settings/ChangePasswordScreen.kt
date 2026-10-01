package com.agarthavision.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/** Actions [ChangePasswordContent] dispatches back to the [ChangePasswordViewModel]. */
data class ChangePasswordActions(
    val onCurrentPasswordChanged: (String) -> Unit,
    val onNewPasswordChanged: (String) -> Unit,
    val onConfirmPasswordChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * Change password, reached from Settings (14zcqntjph9). Online only: offline, the screen says so
 * and the button stays disabled, rather than failing on tap.
 */
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePasswordContent(
        state = state,
        actions = ChangePasswordActions(
            onCurrentPasswordChanged = viewModel::onCurrentPasswordChanged,
            onNewPasswordChanged = viewModel::onNewPasswordChanged,
            onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
            onSubmit = viewModel::onSubmit,
            onBack = onBack,
        ),
    )
}

@Composable
internal fun ChangePasswordContent(
    state: ChangePasswordUiState,
    actions: ChangePasswordActions,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        ChangePasswordAppBar(onBack = actions.onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (state.isChanged) {
                ChangedPanel(onDone = actions.onBack)
            } else {
                ChangePasswordForm(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun ChangePasswordAppBar(onBack: () -> Unit) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
    ) {
        BackArrow(onBack = onBack)
        Text(
            text = stringResource(R.string.change_password_title),
            style = MaterialTheme.typography.headlineSmall,
            color = if (colors.isDark) Color.White else Color.Black,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun ChangePasswordForm(state: ChangePasswordUiState, actions: ChangePasswordActions) {
    val colors = AgarthaTheme.colors
    Text(
        text = stringResource(R.string.change_password_intro),
        color = colors.textSecondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(16.dp))

    if (state.isOffline) {
        NoticeBox(
            text = stringResource(R.string.change_password_offline_notice),
            danger = false,
            modifier = Modifier.testTag(CHANGE_PASSWORD_OFFLINE_TAG),
        )
        Spacer(Modifier.height(16.dp))
    }

    PasswordField(
        label = stringResource(R.string.change_password_current_label),
        value = state.currentPassword,
        onValueChange = actions.onCurrentPasswordChanged,
        error = state.currentError?.let { fieldErrorText(it, null) },
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(14.dp))
    PasswordField(
        label = stringResource(R.string.change_password_new_label),
        value = state.newPassword,
        onValueChange = actions.onNewPasswordChanged,
        error = state.newError?.let { fieldErrorText(it, state.weakPasswordDetail) },
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(14.dp))
    PasswordField(
        label = stringResource(R.string.change_password_confirm_label),
        value = state.confirmPassword,
        onValueChange = actions.onConfirmPasswordChanged,
        error = state.confirmError?.let { fieldErrorText(it, null) },
        imeAction = ImeAction.Done,
        onDone = actions.onSubmit,
    )

    state.failure?.let { failure ->
        Spacer(Modifier.height(16.dp))
        NoticeBox(
            text = stringResource(
                when (failure) {
                    ChangePasswordFailure.NO_CONNECTION -> R.string.change_password_failed_offline
                    ChangePasswordFailure.FAILED -> R.string.change_password_failed
                },
            ),
            danger = true,
        )
    }

    Spacer(Modifier.height(22.dp))
    AgarthaButton(
        onClick = actions.onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CHANGE_PASSWORD_SUBMIT_TAG),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onBrandFill,
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(R.string.change_password_submit))
        }
    }
}

@Composable
private fun fieldErrorText(error: PasswordFieldError, weakDetail: String?): String = when (error) {
    PasswordFieldError.REQUIRED -> stringResource(R.string.change_password_error_required)
    PasswordFieldError.WRONG_CURRENT -> stringResource(R.string.change_password_error_wrong_current)
    PasswordFieldError.MISMATCH -> stringResource(R.string.change_password_error_mismatch)
    PasswordFieldError.SAME_AS_CURRENT -> stringResource(R.string.change_password_error_same)
    PasswordFieldError.WEAK -> listOfNotNull(stringResource(R.string.change_password_error_weak), weakDetail)
        .joinToString(" ")
}

@Composable
private fun ChangedPanel(onDone: () -> Unit) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CHANGE_PASSWORD_DONE_TAG)
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = colors.successText,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.change_password_done_title),
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.change_password_done_body),
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))
        AgarthaButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.change_password_done))
        }
    }
}

@Composable
private fun NoticeBox(text: String, danger: Boolean, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    val tint = if (danger) colors.dangerTint else colors.warningTint
    val edge = if (danger) colors.danger else colors.warning
    val ink = if (danger) colors.dangerText else colors.warningText
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tint, RoundedCornerShape(12.dp))
            .border(1.dp, edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    }
}

/** A labelled password field with a show/hide toggle, styled like the login form's fields. */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    val colors = AgarthaTheme.colors
    var visible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        error != null -> colors.danger
        isFocused -> colors.accent
        else -> colors.borderStrong
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (error != null) colors.dangerTint else colors.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 13.dp)) { innerTextField() }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (visible) R.string.change_password_hide else R.string.change_password_show,
                            ),
                            tint = colors.textSecondary,
                        )
                    }
                }
            },
        )
        if (error != null) {
            Text(text = error, color = colors.danger, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Test tags for [ChangePasswordContent]. */
const val CHANGE_PASSWORD_OFFLINE_TAG = "change_password_offline"
const val CHANGE_PASSWORD_SUBMIT_TAG = "change_password_submit"
const val CHANGE_PASSWORD_DONE_TAG = "change_password_done"
