package com.scrollbuddy.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollbuddy.R
import com.scrollbuddy.settings.BuddyPrefs
import com.scrollbuddy.settings.BuddySettings

private const val PAGE_WELCOME = 0
private const val PAGE_FEATURES = 1
private const val PAGE_SETUP = 2
private const val LAST_PAGE = PAGE_SETUP

private class Feature(@param:DrawableRes val icon: Int, @param:StringRes val title: Int, @param:StringRes val body: Int)

private val FEATURES = listOf(
    Feature(R.drawable.ic_reels, R.string.feature_only_title, R.string.feature_only_body),
    Feature(R.drawable.ic_clock, R.string.feature_limit_title, R.string.feature_limit_body),
    Feature(R.drawable.ic_zero, R.string.feature_zero_title, R.string.feature_zero_body),
    Feature(R.drawable.ic_bubble, R.string.feature_count_title, R.string.feature_count_body),
    Feature(R.drawable.ic_chart, R.string.feature_history_title, R.string.feature_history_body),
    Feature(R.drawable.ic_lock, R.string.feature_private_title, R.string.feature_private_body),
)

/**
 * The three pages shown the first time the app is opened: who Vaathi is, what he does, and the
 * person's own rules. [onDone] is called when they finish or skip.
 */
@Composable
fun OnboardingScreen(
    prefs: BuddyPrefs,
    settings: BuddySettings,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_WELCOME) }
    BackHandler(enabled = page > PAGE_WELCOME) { page-- }

    val colors = MaterialTheme.colorScheme
    // A Surface, so that text with no colour of its own takes the theme's and not black
    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.primaryContainer.copy(alpha = TOP_GLOW_ALPHA),
                        TOP_GLOW_END to colors.background,
                    ),
                )
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (page > PAGE_WELCOME) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalIconButton(onClick = { page-- }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.onboarding_back))
                    }
                    val progress by animateFloatAsState(page / LAST_PAGE.toFloat())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f),
                        drawStopIndicator = {},
                    )
                }
            }
            AnimatedContent(targetState = page, modifier = Modifier.weight(1f)) { shown ->
                when (shown) {
                    PAGE_WELCOME -> WelcomePage()
                    PAGE_FEATURES -> FeaturesPage()
                    else -> SetupPage(prefs, settings)
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page == PAGE_WELCOME) {
                    FilledTonalButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
                Button(
                    onClick = { if (page == LAST_PAGE) onDone() else page++ },
                    modifier = Modifier.weight(BIG_BUTTON_WEIGHT),
                ) {
                    Text(
                        stringResource(
                            when (page) {
                                PAGE_WELCOME -> R.string.onboarding_start
                                LAST_PAGE -> R.string.onboarding_finish
                                else -> R.string.onboarding_continue
                            },
                        ),
                    )
                }
            }
        }
    }
}

// How far down the screen the colour at its top has faded into the background
private const val TOP_GLOW_END = 0.55f

// Thinned so that in the dark theme it is a glow of red, not a block of it
private const val TOP_GLOW_ALPHA = 0.7f
private const val BIG_BUTTON_WEIGHT = 1.6f

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VaathiOnHalo(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_kicker),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The character in his sunglasses, standing in front of a soft round glow. */
@Composable
private fun VaathiOnHalo(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(HALO_WIDTH)
                .aspectRatio(1f)
                .background(colors.surface.copy(alpha = HALO_ALPHA), CircleShape),
        )
        Box(
            Modifier
                .fillMaxWidth(HALO_WIDTH * INNER_HALO)
                .aspectRatio(1f)
                .background(colors.surface.copy(alpha = HALO_ALPHA), CircleShape),
        )
        Image(
            painter = painterResource(R.drawable.vaathi_shades),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

private const val HALO_WIDTH = 0.95f
private const val INNER_HALO = 0.7f
private const val HALO_ALPHA = 0.55f

@Composable
private fun FeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeading(
            title = stringResource(R.string.onboarding_features_title),
            body = stringResource(R.string.onboarding_features_body),
            portraitSize = 96.dp,
        )
        FEATURES.chunked(FEATURE_COLUMNS).forEach { row ->
            // The cards of a row are as tall as the taller of them
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { feature ->
                    FeatureCard(
                        feature = feature,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private const val FEATURE_COLUMNS = 2

@Composable
private fun FeatureCard(feature: Feature, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBadge(feature.icon)
            Text(stringResource(feature.title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(feature.body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupPage(prefs: BuddyPrefs, settings: BuddySettings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeading(
            title = stringResource(R.string.onboarding_setup_title),
            body = stringResource(R.string.onboarding_setup_body),
            portraitSize = 96.dp,
            said = stringResource(R.string.onboarding_setup_bubble),
        )
        Text(stringResource(R.string.settings_address), style = MaterialTheme.typography.titleMedium)
        AddressPicker(prefs, settings)
        Text(
            text = stringResource(R.string.limit_title),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LimitModePicker(prefs, settings)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The character's face in a round frame over a page's title, with [said] as a line in a small
 * bubble under it when he has something to say.
 */
@Composable
private fun PageHeading(title: String, body: String, portraitSize: Dp, said: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_head),
            contentDescription = null,
            modifier = Modifier
                .size(portraitSize)
                .clip(CircleShape)
                .background(colors.secondaryContainer),
        )
        if (said != null) {
            Surface(
                modifier = Modifier.padding(top = 8.dp),
                shape = CircleShape,
                color = colors.surfaceContainerLowest,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = said,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
