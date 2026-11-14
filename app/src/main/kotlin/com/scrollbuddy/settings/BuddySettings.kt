package com.scrollbuddy.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.scrollbuddy.R
import com.scrollbuddy.detector.DetectorConfig
import com.scrollbuddy.detector.LimitMode
import com.scrollbuddy.detector.ShortVideoFeed
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

enum class TrackedApp(val feed: ShortVideoFeed, @param:StringRes val label: Int) {
    Instagram(ShortVideoFeed.InstagramReels, R.string.app_instagram),
    YouTube(ShortVideoFeed.YouTubeShorts, R.string.app_youtube);

    val packageName: String get() = feed.packageName
}

/** What the character calls the person; [label] is the choice's name on screen. */
enum class AddressTerm(@param:StringRes val label: Int) {
    Nanba(R.string.address_nanba),
    Nanbi(R.string.address_nanbi),

    /** A word of the person's own, kept in [BuddyPrefs.customAddress]. */
    Custom(R.string.address_custom),
}

data class BuddyPrefs(
    val limitMode: LimitMode,
    val nudgeAfterScrolls: Int,
    val nudgeAfterMinutes: Int,
    val trackedPackages: Set<String>,
    val addressTerm: AddressTerm,
    val customAddress: String,
    val onboarded: Boolean,
) {
    fun toDetectorConfig() = DetectorConfig(
        trackedPackages = trackedPackages,
        mode = limitMode,
        nudgeAfterSwipes = nudgeAfterScrolls,
        nudgeAfterMs = nudgeAfterMinutes * MS_PER_MINUTE,
    )

    /** The word the character uses for the person. An empty word of their own falls back to Nanba. */
    fun address(resources: Resources): String = when {
        addressTerm != AddressTerm.Custom -> resources.getString(addressTerm.label)
        customAddress.isNotBlank() -> customAddress
        else -> resources.getString(AddressTerm.Nanba.label)
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}

/** The activity writes these and the watch service reads them; both run in one process. */
class BuddySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val values: Flow<BuddyPrefs> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun read() = BuddyPrefs(
        limitMode = prefs.getString(KEY_LIMIT_MODE, null)
            ?.let { name -> LimitMode.entries.firstOrNull { it.name == name } }
            ?: LimitMode.Swipes,
        nudgeAfterScrolls = prefs.getInt(KEY_NUDGE_AFTER_SCROLLS, DEFAULT_NUDGE_AFTER_SCROLLS),
        nudgeAfterMinutes = prefs.getInt(KEY_NUDGE_AFTER_MINUTES, DEFAULT_NUDGE_AFTER_MINUTES),
        // Copied because the set SharedPreferences returns must not be modified or held on to
        trackedPackages = prefs.getStringSet(KEY_TRACKED, null)?.toSet()
            ?: TrackedApp.entries.mapTo(HashSet()) { it.packageName },
        addressTerm = prefs.getString(KEY_ADDRESS_TERM, null)
            ?.let { name -> AddressTerm.entries.firstOrNull { it.name == name } }
            ?: AddressTerm.Nanba,
        customAddress = prefs.getString(KEY_CUSTOM_ADDRESS, null).orEmpty(),
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
    )

    fun setLimitMode(mode: LimitMode) = prefs.edit { putString(KEY_LIMIT_MODE, mode.name) }

    fun setNudgeAfterScrolls(scrolls: Int) =
        prefs.edit { putInt(KEY_NUDGE_AFTER_SCROLLS, scrolls.coerceIn(SCROLLS_ALLOWED)) }

    fun setNudgeAfterMinutes(minutes: Int) =
        prefs.edit { putInt(KEY_NUDGE_AFTER_MINUTES, minutes.coerceIn(MINUTES_ALLOWED)) }

    fun setTracked(app: TrackedApp, tracked: Boolean) {
        val current = read().trackedPackages
        val updated = if (tracked) current + app.packageName else current - app.packageName
        prefs.edit { putStringSet(KEY_TRACKED, updated) }
    }

    fun setAddressTerm(term: AddressTerm) = prefs.edit { putString(KEY_ADDRESS_TERM, term.name) }

    /** Kept to one short line: it is dropped into the middle of the character's sentences. */
    fun setCustomAddress(address: String) {
        val oneLine = address.filterNot { it.isISOControl() }.trim().take(MAX_ADDRESS_LENGTH)
        prefs.edit { putString(KEY_CUSTOM_ADDRESS, oneLine) }
    }

    fun setOnboarded() = prefs.edit { putBoolean(KEY_ONBOARDED, true) }

    companion object {
        // What the seek bars cover, and the wider limits a typed number may reach
        val SCROLLS_RANGE = 5..100 step 5
        val MINUTES_RANGE = 1..60
        val SCROLLS_ALLOWED = 1..500
        val MINUTES_ALLOWED = 1..180
        const val MAX_ADDRESS_LENGTH = 20

        private const val FILE_NAME = "buddy_settings"
        private const val KEY_LIMIT_MODE = "limit_mode"
        private const val KEY_NUDGE_AFTER_SCROLLS = "nudge_after_scrolls"
        private const val KEY_NUDGE_AFTER_MINUTES = "nudge_after_minutes"
        private const val KEY_TRACKED = "tracked_packages"
        private const val KEY_ADDRESS_TERM = "address_term"
        private const val KEY_CUSTOM_ADDRESS = "custom_address"
        private const val KEY_ONBOARDED = "onboarded"
        private const val DEFAULT_NUDGE_AFTER_SCROLLS = 20
        private const val DEFAULT_NUDGE_AFTER_MINUTES = 10
    }
}
