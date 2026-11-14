package com.scrollbuddy.ui

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollbuddy.R
import com.scrollbuddy.detector.LimitMode
import com.scrollbuddy.settings.AddressTerm
import com.scrollbuddy.settings.BuddyPrefs
import com.scrollbuddy.settings.BuddySettings
import kotlin.math.roundToInt

/** An icon on a round patch of colour, as the cards and options carry. */
@Composable
fun IconBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(40.dp)
            .background(if (selected) colors.primary else colors.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) colors.onPrimary else colors.onSecondaryContainer,
        )
    }
}

/** One of the rules on its own card, under its icon and [title]. */
@Composable
fun RuleSection(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/**
 * The three limits as a group of radio options, of which one is in force. The one chosen opens
 * to show its number, which can be stepped, typed or slid.
 */
@Composable
fun LimitModePicker(prefs: BuddyPrefs, settings: BuddySettings, modifier: Modifier = Modifier) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LimitOption(
            icon = R.drawable.ic_reels,
            title = stringResource(R.string.limit_swipes_title),
            body = stringResource(R.string.limit_swipes_body),
            selected = prefs.limitMode == LimitMode.Swipes,
            onSelect = { settings.setLimitMode(LimitMode.Swipes) },
        ) {
            LimitValueEditor(
                value = prefs.nudgeAfterScrolls,
                seekRange = BuddySettings.SCROLLS_RANGE,
                allowed = BuddySettings.SCROLLS_ALLOWED,
                unit = R.plurals.unit_swipes,
                onValueChange = settings::setNudgeAfterScrolls,
            )
        }
        LimitOption(
            icon = R.drawable.ic_clock,
            title = stringResource(R.string.limit_time_title),
            body = stringResource(R.string.limit_time_body),
            selected = prefs.limitMode == LimitMode.Time,
            onSelect = { settings.setLimitMode(LimitMode.Time) },
        ) {
            LimitValueEditor(
                value = prefs.nudgeAfterMinutes,
                seekRange = BuddySettings.MINUTES_RANGE,
                allowed = BuddySettings.MINUTES_ALLOWED,
                unit = R.plurals.unit_minutes,
                onValueChange = settings::setNudgeAfterMinutes,
            )
        }
        LimitOption(
            icon = R.drawable.ic_zero,
            title = stringResource(R.string.limit_zero_title),
            body = stringResource(R.string.limit_zero_body),
            selected = prefs.limitMode == LimitMode.Zero,
            onSelect = { settings.setLimitMode(LimitMode.Zero) },
        )
    }
}

@Composable
private fun LimitOption(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    selected: Boolean,
    onSelect: () -> Unit,
    editor: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Clipped first, so the press ripple has the card's round corners
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer.copy(alpha = SELECTED_FILL_ALPHA) else Color.Transparent,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, selected = selected)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                // The whole option is the radio button; this is only its mark
                RadioButton(selected = selected, onClick = null)
            }
            if (editor != null) {
                AnimatedVisibility(visible = selected) { editor() }
            }
        }
    }
}

private const val SELECTED_FILL_ALPHA = 0.4f

/**
 * A limit's number with three ways to set it: a step either way, typing, and a seek bar. The
 * seek bar covers [seekRange], the usual values; a typed number may be anything in [allowed].
 */
@Composable
private fun LimitValueEditor(
    value: Int,
    seekRange: IntProgression,
    allowed: IntRange,
    @PluralsRes unit: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(R.drawable.ic_minus, R.string.limit_less, enabled = value > allowed.first) {
                onValueChange(value - 1)
            }
            NumberField(value, allowed, onValueChange)
            StepButton(R.drawable.ic_plus, R.string.limit_more, enabled = value < allowed.last) {
                onValueChange(value + 1)
            }
            // On one line whatever the width: on a narrow phone the word was split in two
            Text(
                text = pluralStringResource(unit, value),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                softWrap = false,
                maxLines = 1,
            )
        }
        SeekBar(value, seekRange, onValueChange)
        Row {
            Text(seekRange.first.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(
                text = seekRange.last.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = stringResource(R.string.limit_typed_range, allowed.first, allowed.last),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, @StringRes label: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/** The number itself, which can be typed over. Only a number in [allowed] is passed on. */
@Composable
private fun NumberField(value: Int, allowed: IntRange, onValueChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    // What is being typed, which for a moment may be empty or out of range. A number set any
    // other way, by the seek bar or a step, replaces it
    var typed by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = typed,
        onValueChange = { text ->
            typed = text.filter(Char::isDigit).take(allowed.last.toString().length)
            typed.toIntOrNull()?.takeIf { it in allowed }?.let(onValueChange)
        },
        modifier = Modifier
            .width(NUMBER_FIELD_WIDTH)
            // Leaving the field with nothing usable in it puts the number in force back
            .onFocusChanged { if (!it.isFocused) typed = value.toString() },
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.primary, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.surfaceContainerLowest,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) { field() }
            }
        },
    )
}

private val NUMBER_FIELD_WIDTH = 72.dp

/**
 * A plain bar with a round thumb in place of Material's notched track and row of dots. It still
 * stops only on the values of [range]; a typed [value] between two stops, or beyond the ends,
 * is shown at the nearest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(value: Int, range: IntProgression, onValueChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val barColors = SliderDefaults.colors(
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.surfaceContainerHighest,
    )
    Slider(
        value = value.coerceIn(range.first, range.last).toFloat(),
        onValueChange = { slid ->
            val stop = slid.roundToInt()
            if (stop != value) onValueChange(stop)
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        // Slider counts only the stops between the two ends
        steps = (range.last - range.first) / range.step - 1,
        thumb = {
            Box(
                Modifier
                    .size(THUMB_SIZE)
                    .shadow(3.dp, CircleShape)
                    // White in both themes: a dark ring disappears into the dark track
                    .background(Color.White, CircleShape)
                    .padding(THUMB_RING)
                    .background(colors.primary, CircleShape),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(TRACK_HEIGHT),
                colors = barColors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
                thumbTrackGapSize = 0.dp,
            )
        },
    )
}

private val THUMB_SIZE = 26.dp
private val THUMB_RING = 5.dp
private val TRACK_HEIGHT = 8.dp

/** What the character calls the person: one of his two words, or one of their own. */
@Composable
fun AddressPicker(prefs: BuddyPrefs, settings: BuddySettings, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddressTerm.entries.forEach { term ->
                FilterChip(
                    selected = term == prefs.addressTerm,
                    onClick = { settings.setAddressTerm(term) },
                    label = { Text(stringResource(term.label)) },
                )
            }
        }
        AnimatedVisibility(visible = prefs.addressTerm == AddressTerm.Custom) {
            // The field keeps its own text: what is stored comes back trimmed, which would
            // swallow a space while a second word is being typed
            var draft by rememberSaveable { mutableStateOf(prefs.customAddress) }
            OutlinedTextField(
                value = draft,
                onValueChange = { typed ->
                    draft = typed.take(BuddySettings.MAX_ADDRESS_LENGTH)
                    settings.setCustomAddress(draft)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text(stringResource(R.string.address_custom_hint)) },
                singleLine = true,
            )
        }
    }
}
