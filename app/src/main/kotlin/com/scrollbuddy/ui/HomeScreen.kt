package com.scrollbuddy.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scrollbuddy.R
import com.scrollbuddy.detector.LimitMode
import com.scrollbuddy.detector.Nudge
import com.scrollbuddy.history.DayUsage
import com.scrollbuddy.history.HistoryRange
import com.scrollbuddy.history.UsageTotals
import com.scrollbuddy.history.summarize
import com.scrollbuddy.overlay.NudgeScene
import com.scrollbuddy.overlay.nudgeLine
import com.scrollbuddy.overlay.nudgeMoreLabel
import com.scrollbuddy.overlay.nudgeStats
import com.scrollbuddy.service.ScrollWatchService
import com.scrollbuddy.settings.BuddyPrefs
import com.scrollbuddy.settings.BuddySettings
import com.scrollbuddy.settings.TrackedApp
import java.time.LocalDate

/** [address] is what the character calls the person. */
@Composable
fun HomeScreen(
    prefs: BuddyPrefs,
    settings: BuddySettings,
    entries: List<DayUsage>,
    address: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var onDuty by remember { mutableStateOf(ScrollWatchService.isEnabled(context)) }
    // The switch is in system Settings, so check again each time the person comes back
    LifecycleResumeEffect(Unit) {
        onDuty = ScrollWatchService.isEnabled(context)
        onPauseOrDispose { }
    }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            address = address,
            onDuty = onDuty,
            onTurnOn = { showConsent = true },
            onPreview = { showPreview = true },
        )
        TodayCard(entries, address)
        Rules(prefs, settings)
    }

    if (showConsent) {
        ConsentDialog(
            onAgree = {
                showConsent = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDecline = { showConsent = false },
        )
    }
    if (showPreview) NudgePreview(address, prefs.limitMode, onClose = { showPreview = false })
}

/** The greeting, the character waving beside it, whether he is on duty and what to do about it. */
@Composable
private fun HeroCard(address: String, onDuty: Boolean, onTurnOn: () -> Unit, onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(colors.primaryContainer.copy(alpha = HERO_TINT_ALPHA), colors.surfaceContainerLow),
                    ),
                )
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.home_greeting, address), style = MaterialTheme.typography.headlineSmall)
                    DutyChip(onDuty)
                    Text(
                        text = stringResource(if (onDuty) R.string.status_on_body else R.string.status_off_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Vaathi(clip = VaathiClip.Wave, modifier = Modifier.height(HERO_CHARACTER_HEIGHT), loop = false)
            }
            if (!onDuty) {
                Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.status_off_action))
                }
            }
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.status_preview))
            }
        }
    }
}

private val HERO_CHARACTER_HEIGHT = 190.dp

// Thinned so that in the dark theme the card's top is a glow of red, not a block of it
private const val HERO_TINT_ALPHA = 0.7f

@Composable
private fun DutyChip(onDuty: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (onDuty) colors.secondary else colors.surfaceContainerHighest) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (onDuty) colors.onSecondary else colors.outline, CircleShape),
            )
            Text(
                text = stringResource(if (onDuty) R.string.status_on_chip else R.string.status_off_chip),
                style = MaterialTheme.typography.labelLarge,
                color = if (onDuty) colors.onSecondary else colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the accessibility service sees and does, shown before the person is sent to the system
 * switch. Google Play requires this disclosure and an explicit agreement inside the app.
 */
@Composable
private fun ConsentDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.consent_title)) },
        text = {
            Text(
                text = stringResource(R.string.consent_body),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { Button(onClick = onAgree) { Text(stringResource(R.string.consent_agree)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.consent_decline)) } },
    )
}

/** Plays the nudge inside the app, as [mode] would bring it, before anything is switched on. */
@Composable
private fun NudgePreview(address: String, mode: LimitMode, onClose: () -> Unit) {
    val resources = LocalResources.current
    val nudge = remember(mode) { SAMPLE_NUDGE.copy(mode = mode) }
    val line = remember(address, mode) { nudgeLine(resources, address, mode) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NudgeScene(
            line = line,
            stats = nudgeStats(resources, nudge),
            moreLabel = nudgeMoreLabel(resources, nudge),
            onMore = onClose,
            onTakeBreak = onClose,
        )
    }
}

private val SAMPLE_NUDGE = Nudge(
    packageName = TrackedApp.Instagram.packageName,
    swipes = 25,
    elapsedMs = 12 * 60_000L,
    count = 1,
    mode = LimitMode.Swipes,
)

/** Today's swipes in each app and the time they took, as three tiles. */
@Composable
private fun TodayCard(entries: List<DayUsage>, address: String) {
    val today = remember(entries) { summarize(entries, HistoryRange.Today, LocalDate.now()) }
    fun swipesIn(app: TrackedApp) = (today.byPackage[app.packageName] ?: UsageTotals()).swipes
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.today_reels), swipesIn(TrackedApp.Instagram).toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.today_shorts), swipesIn(TrackedApp.YouTube).toString(), Modifier.weight(1f))
            StatTile(
                label = stringResource(R.string.today_time),
                value = formatDuration(today.total.watchedMs, compact = true),
                modifier = Modifier.weight(1f),
            )
        }
        if (today.total.swipes == 0) {
            Text(
                text = stringResource(R.string.today_empty, address),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One number with its name over it. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** The rules, each on a card of its own: when he comes, where he looks, and what he calls you. */
@Composable
private fun Rules(prefs: BuddyPrefs, settings: BuddySettings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        RuleSection(R.drawable.ic_clock, stringResource(R.string.limit_title)) {
            LimitModePicker(prefs, settings)
        }
        RuleSection(R.drawable.ic_reels, stringResource(R.string.settings_watch_apps)) {
            Column {
                TrackedApp.entries.forEach { app ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(app.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = app.packageName in prefs.trackedPackages,
                            onCheckedChange = { settings.setTracked(app, it) },
                        )
                    }
                }
            }
        }
        RuleSection(R.drawable.ic_bubble, stringResource(R.string.settings_address)) {
            AddressPicker(prefs, settings)
        }
    }
}
