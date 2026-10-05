package com.agarthavision.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.R
import com.agarthavision.ui.components.rememberReducedMotion
import com.agarthavision.ui.theme.AgarthaTheme
import kotlinx.coroutines.launch

private data class OnboardingStep(
    @DrawableRes val illustrationLight: Int,
    @DrawableRes val illustrationDark: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val illustrationDescriptionRes: Int,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        illustrationLight = R.drawable.ill_onboarding_1_light,
        illustrationDark = R.drawable.ill_onboarding_1_dark,
        titleRes = R.string.onboarding_step1_title,
        bodyRes = R.string.onboarding_step1_body,
        illustrationDescriptionRes = R.string.onboarding_step1_illustration,
    ),
    OnboardingStep(
        illustrationLight = R.drawable.ill_onboarding_2_light,
        illustrationDark = R.drawable.ill_onboarding_2_dark,
        titleRes = R.string.onboarding_step2_title,
        bodyRes = R.string.onboarding_step2_body,
        illustrationDescriptionRes = R.string.onboarding_step2_illustration,
    ),
    OnboardingStep(
        illustrationLight = R.drawable.ill_onboarding_3_light,
        illustrationDark = R.drawable.ill_onboarding_3_dark,
        titleRes = R.string.onboarding_step3_title,
        bodyRes = R.string.onboarding_step3_body,
        illustrationDescriptionRes = R.string.onboarding_step3_illustration,
    ),
    OnboardingStep(
        illustrationLight = R.drawable.ill_onboarding_4_light,
        illustrationDark = R.drawable.ill_onboarding_4_dark,
        titleRes = R.string.onboarding_step4_title,
        bodyRes = R.string.onboarding_step4_body,
        illustrationDescriptionRes = R.string.onboarding_step4_illustration,
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingContent(
        onFinish = {
            viewModel.onCompleteOnboarding()
            onComplete()
        },
        reduceMotion = rememberReducedMotion(),
    )
}

private suspend fun PagerState.goTo(page: Int, reduceMotion: Boolean) {
    if (reduceMotion) scrollToPage(page) else animateScrollToPage(page)
}

@Composable
internal fun OnboardingContent(
    onFinish: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { onboardingSteps.size })
    var playedMask by rememberSaveable { mutableIntStateOf(0) }

    // Handle system back button when on step > 0
    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.goTo(pagerState.currentPage - 1, reduceMotion)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val alreadyPlayed = (playedMask shr page) and 1 == 1
                val step = onboardingSteps[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OnboardingPage(
                        step = step,
                        revealed = alreadyPlayed || reduceMotion,
                        play = pagerState.settledPage == page && !alreadyPlayed && !reduceMotion,
                        onPlayed = { playedMask = playedMask or (1 shl page) },
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = stringResource(step.titleRes),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                        modifier = Modifier.semantics { heading() },
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

            // Step Indicator Dots
            val progressLabel = stringResource(
                R.string.onboarding_step_progress,
                pagerState.currentPage + 1,
                onboardingSteps.size,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .testTag("onboardingProgress")
                    .clearAndSetSemantics {
                        contentDescription = progressLabel
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                onboardingSteps.indices.forEach { index ->
                    val isActive = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 8.dp,
                        animationSpec = if (reduceMotion) snap() else tween(200),
                        label = "dotWidth",
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isActive) colors.accent else colors.border,
                        animationSpec = if (reduceMotion) snap() else tween(200),
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }

            // Navigation CTA Button
            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingSteps.size - 1) {
                        coroutineScope.launch {
                            pagerState.goTo(pagerState.currentPage + 1, reduceMotion)
                        }
                    } else {
                        onFinish()
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
                    text = if (pagerState.currentPage < onboardingSteps.size - 1) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_finish)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pagerState.currentPage < onboardingSteps.size - 1) {
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

        // Back Button Top Left (on step > 0)
        if (pagerState.currentPage > 0) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.goTo(pagerState.currentPage - 1, reduceMotion)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
                    .testTag("onboardingBack"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                    tint = colors.textSecondary,
                )
            }
        }

        // Skip Button Top Right (on step < last)
        if (pagerState.currentPage < onboardingSteps.size - 1) {
            TextButton(
                onClick = onFinish,
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
    }
}

@Composable
private fun OnboardingPage(
    step: OnboardingStep,
    revealed: Boolean,
    play: Boolean,
    onPlayed: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(play) {
        if (play) {
            progress.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            onPlayed()
        }
    }
    Image(
        painter = painterResource(if (colors.isDark) step.illustrationDark else step.illustrationLight),
        contentDescription = stringResource(step.illustrationDescriptionRes),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 300.dp)
            .aspectRatio(300f / 260f)
            .graphicsLayer {
                val p = progress.value
                alpha = p
                val s = 0.96f + 0.04f * p
                scaleX = s
                scaleY = s
                translationY = (1f - p) * 16.dp.toPx()
            },
    )
}
