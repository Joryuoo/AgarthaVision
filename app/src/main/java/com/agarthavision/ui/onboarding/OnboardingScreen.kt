package com.agarthavision.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

private data class OnboardingStep(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Outlined.Layers,
        titleRes = R.string.onboarding_step1_title,
        bodyRes = R.string.onboarding_step1_body,
    ),
    OnboardingStep(
        icon = Icons.Outlined.CheckCircle,
        titleRes = R.string.onboarding_step2_title,
        bodyRes = R.string.onboarding_step2_body,
    ),
    OnboardingStep(
        icon = Icons.Outlined.BarChart,
        titleRes = R.string.onboarding_step3_title,
        bodyRes = R.string.onboarding_step3_body,
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val colors = AgarthaTheme.colors
    var currentStep by remember { mutableIntStateOf(0) }

    fun finishOnboarding() {
        viewModel.onCompleteOnboarding()
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Skip Button Top Right
        if (currentStep < onboardingSteps.size - 1) {
            TextButton(
                onClick = ::finishOnboarding,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp)
                    .testTag("onboardingSkip"),
            ) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Animated Step Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { slideTransition() },
                label = "OnboardingContentTransition",
            ) { stepIndex ->
                val step = onboardingSteps[stepIndex]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(colors.brandFill)
                            .border(1.dp, colors.border, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = colors.onBrandFill,
                            modifier = Modifier.size(56.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = stringResource(step.titleRes),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(step.bodyRes),
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Step Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                onboardingSteps.indices.forEach { index ->
                    val isActive = index == currentStep
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) colors.accent else colors.border),
                    )
                }
            }

            // Navigation CTA Button
            Button(
                onClick = {
                    if (currentStep < onboardingSteps.size - 1) {
                        currentStep++
                    } else {
                        finishOnboarding()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboardingNext"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandFill,
                    contentColor = colors.onBrandFill,
                ),
            ) {
                Text(
                    text = if (currentStep < onboardingSteps.size - 1) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_finish)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (currentStep < onboardingSteps.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = colors.onBrandFill,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun slideTransition(): ContentTransform {
    return (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
        slideOutHorizontally { width -> -width } + fadeOut(),
    )
}
