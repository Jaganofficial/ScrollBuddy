package com.scrollbuddy.history

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.LocalDate

/**
 * Swipes and watching time per app per day, kept on the phone only. One small entry a day for
 * each app is all there is, so a preferences file is enough. The watch service writes and the
 * history screen reads; both run in one process.
 */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val entries: Flow<List<DayUsage>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun read(): List<DayUsage> = prefs.all.mapNotNull { (key, value) -> decodeUsage(key, value as? String) }

    /** Adds one swipe, and the time spent on the video that was swiped away, to [date]. */
    fun addSwipe(packageName: String, watchedMs: Long, date: LocalDate = LocalDate.now()) {
        val key = usageKey(date, packageName)
        val current = decodeUsage(key, prefs.getString(key, null))
        val updated = UsageTotals(current?.swipes ?: 0, current?.watchedMs ?: 0L) + UsageTotals(1, watchedMs)
        prefs.edit { putString(key, encodeUsage(updated)) }
    }

    /** How many swipes [packageName] has had on [date]. */
    fun swipesOn(date: LocalDate, packageName: String): Int {
        val key = usageKey(date, packageName)
        return decodeUsage(key, prefs.getString(key, null))?.swipes ?: 0
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val FILE_NAME = "scroll_history"
    }
}
