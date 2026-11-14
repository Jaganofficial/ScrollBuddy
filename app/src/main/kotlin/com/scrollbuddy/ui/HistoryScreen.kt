package com.scrollbuddy.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scrollbuddy.R
import com.scrollbuddy.history.DayUsage
import com.scrollbuddy.history.HistoryBucket
import com.scrollbuddy.history.HistoryRange
import com.scrollbuddy.history.HistorySummary
import com.scrollbuddy.history.UsageTotals
import com.scrollbuddy.history.isMonthlySpan
import com.scrollbuddy.history.summarize
import com.scrollbuddy.settings.TrackedApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@get:StringRes
private val HistoryRange.label: Int
    get() = when (this) {
        HistoryRange.Today -> R.string.history_range_today
        HistoryRange.Week -> R.string.history_range_week
        HistoryRange.Month -> R.string.history_range_month
        HistoryRange.Year -> R.string.history_range_year
        HistoryRange.AllTime -> R.string.history_range_all
    }

/** [address] is what the character calls the person. */
@Composable
fun HistoryScreen(
    entries: List<DayUsage>,
    address: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.Week) }
    // The first and last day the person chose. They are kept while a chip is in use, so they
    // are still there to go back to
    var chosenDates by rememberSaveable { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showChosenDates by rememberSaveable { mutableStateOf(false) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    // Read again on every return to the app: it may have been left open overnight
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    val dates = chosenDates.takeIf { showChosenDates }
    val monthly = if (dates != null) isMonthlySpan(dates.first, dates.second) else range.isMonthly
    val summary = remember(entries, range, dates, today) {
        if (dates != null) {
            summarize(entries, dates.first, dates.second, monthly)
        } else {
            summarize(entries, range, today)
        }
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach {
                FilterChip(
                    selected = dates == null && it == range,
                    onClick = {
                        range = it
                        showChosenDates = false
                    },
                    label = { Text(stringResource(it.label)) },
                )
            }
            FilterChip(
                selected = dates != null,
                onClick = { pickingDates = true },
                label = {
                    Text(
                        chosenDates?.let { (first, last) ->
                            stringResource(
                                R.string.history_range_custom_value,
                                SHORT_DAY_FORMAT.format(first),
                                SHORT_DAY_FORMAT.format(last),
                            )
                        } ?: stringResource(R.string.history_range_custom),
                    )
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, Modifier.size(18.dp))
                },
            )
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (summary.total.swipes == 0) {
                EmptyHistory(address, nothingAtAll = entries.isEmpty())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.history_total_swipes),
                        value = summary.total.swipes.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.history_total_time),
                        value = formatDuration(summary.total.watchedMs),
                        modifier = Modifier.weight(1f),
                    )
                }
                ByAppCard(summary)
                if (summary.buckets.size > 1) BucketsCard(summary.buckets, monthly, today)
            }
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
    }

    if (pickingDates) {
        DatesDialog(
            chosen = chosenDates,
            today = today,
            onChoose = { first, last ->
                chosenDates = first to last
                showChosenDates = true
                pickingDates = false
            },
            onCancel = { pickingDates = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.history_clear_cancel)) }
            },
        )
    }
}

/**
 * A calendar to pick a first and a last day from, none later than [today]. The picker works in
 * milliseconds at midnight UTC, so days are turned to and from that here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesDialog(
    chosen: Pair<LocalDate, LocalDate>?,
    today: LocalDate,
    onChoose: (first: LocalDate, last: LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    val lastSelectableMs = remember(today) { today.toUtcMs() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = chosen?.first?.toUtcMs(),
        initialSelectedEndDateMillis = chosen?.second?.toUtcMs(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastSelectableMs

            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val firstMs = state.selectedStartDateMillis
    // One day tapped and no second is that one day
    val lastMs = state.selectedEndDateMillis ?: firstMs
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = { if (firstMs != null && lastMs != null) onChoose(firstMs.toUtcDay(), lastMs.toUtcDay()) },
                enabled = firstMs != null,
            ) { Text(stringResource(R.string.history_pick_confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.history_pick_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            // The dialog gives its content no height of its own, and the calendar scrolls
            modifier = Modifier
                .height(PICKER_HEIGHT)
                .padding(top = 16.dp),
            title = null,
            showModeToggle = false,
        )
    }
}

private val PICKER_HEIGHT = 460.dp

private fun LocalDate.toUtcMs(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** [nothingAtAll] when the history itself is empty, not just the dates being looked at. */
@Composable
private fun EmptyHistory(address: String, nothingAtAll: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.vaathi_shades),
            contentDescription = null,
            modifier = Modifier.height(220.dp),
        )
        Text(
            text = stringResource(if (nothingAtAll) R.string.history_empty else R.string.history_empty_range, address),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ByAppCard(summary: HistorySummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.history_by_app), style = MaterialTheme.typography.titleMedium)
            TrackedApp.entries.forEach { app ->
                val totals = summary.byPackage[app.packageName] ?: UsageTotals()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(app.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(usageText(totals), style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { totals.swipes.toFloat() / summary.total.swipes },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketsCard(buckets: List<HistoryBucket>, monthly: Boolean, today: LocalDate) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(if (monthly) R.string.history_by_month else R.string.history_by_day),
                style = MaterialTheme.typography.titleMedium,
            )
            BarChart(buckets)
            // Newest first, and only the days or months that have anything in them
            buckets.asReversed().filter { it.totals.swipes > 0 }.forEachIndexed { index, bucket ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = bucketLabel(bucket.start, monthly, today),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(usageText(bucket.totals), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** One bar per bucket, oldest on the left, as tall as its share of the busiest one. */
@Composable
private fun BarChart(buckets: List<HistoryBucket>) {
    val barColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val most = buckets.maxOf { it.totals.swipes }.coerceAtLeast(1)
        val slot = size.width / buckets.size
        val barWidth = slot * BAR_WIDTH_FRACTION
        val corner = CornerRadius(barWidth / 2)
        buckets.forEachIndexed { index, bucket ->
            val left = slot * index + (slot - barWidth) / 2
            drawRoundRect(trackColor, Offset(left, 0f), Size(barWidth, size.height), corner)
            val height = size.height * bucket.totals.swipes / most
            if (height > 0f) {
                drawRoundRect(barColor, Offset(left, size.height - height), Size(barWidth, height), corner)
            }
        }
    }
}

private const val BAR_WIDTH_FRACTION = 0.6f
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM")
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
private fun bucketLabel(start: LocalDate, monthly: Boolean, today: LocalDate): String = when {
    monthly -> MONTH_FORMAT.format(start)
    start == today -> stringResource(R.string.history_bucket_today)
    else -> DAY_FORMAT.format(start)
}

@Composable
private fun usageText(totals: UsageTotals): String = stringResource(
    R.string.usage_summary,
    pluralStringResource(R.plurals.swipes, totals.swipes, totals.swipes),
    formatDuration(totals.watchedMs),
)
