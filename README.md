# ScrollBuddy

The app's name on the phone is **Vaathi ride-uhh!**; ScrollBuddy is the project and package name.

When you've swiped through too many Instagram Reels or YouTube Shorts, a character called
Vaathi comes onto the screen, covers the video and tells you to stop.

## What it watches, and what it can't

ScrollBuddy has one accessibility service and no Android permissions at all.

| Limit | How it is enforced |
|---|---|
| Instagram and YouTube only | `android:packageNames` in `res/xml/scroll_watch_service_config.xml`; the system delivers no events from any other app |
| Two kinds of event only: something scrolling, and a window taking the focus | `android:accessibilityEventTypes="typeViewScrolled\|typeWindowStateChanged"` |
| Can't read the screen | `android:canRetrieveWindowContent="false"` |
| Nothing leaves the phone | No `INTERNET` permission in the manifest |

Inside those two apps only the Reels and Shorts players count. `ShortVideoFeed` recognises each
one's pager by the shape of its scroll events, measured on a phone in November 2026:

| Feed | Scroll events come from | A swipe is |
|---|---|---|
| Instagram Reels | `androidx.viewpager.widget.ViewPager`, moving vertically, with the reel's position as the item index | A change of position |
| YouTube Shorts | `android.widget.FrameLayout` with no item index | A move and, within about 150 ms, an equal move back |

The home feeds, comments, grids, search and the watch page all scroll in `RecyclerView`s, so they
never match. A redesign of either app can change these classes; `ShortVideoFeedTest` and
`SwipeCounterTest` hold the recorded events to compare against a new recording.

## When Vaathi walks in

A session is one visit to the app: it starts at the first swipe after Instagram or YouTube is
opened and ends when the person leaves it. Counts and time start again from nothing at the
next visit. The service hears nothing from other apps, so it learns of a visit ending in these
ways:

| The person | How it is known |
|---|---|
| Taps **Close the app** on the nudge | The app sends them to the home screen itself |
| Leaves and comes back, by the home screen, another app or the notification shade | The same screen of the app takes the focus twice, with nothing of the app's own in between |
| Turns the screen off | The screen-off broadcast |
| Stops swiping for 10 minutes | A safety net, in case a return goes unseen |

A return goes unseen when the app showed a dialog or a passing message just before the person
left and they come back by a way that reports once, such as closing the notification shade.
Coming back from the home screen reports twice and is always seen. The rule is in
`DoomScrollDetector.onWindowFocused`, and it has been tried against YouTube on an emulator
only.

One limit is in force at a time, chosen on the Home tab:

| Limit | The nudge plays |
|---|---|
| After some reels | When the session reaches the number of swipes |
| After some time | When the session has lasted the number of minutes |
| Zero reels | At the first swipe, every time, with no way to ask for more |

**Close the app** sends the person to the home screen and ends the session. The other
button asks for a little more, 5 reels or Shorts or 5 minutes, and the nudge comes back when
that is used up.

While the person swipes, a small bubble at the top of the screen shows the day's count for
that app. It turns into a warning when 3 swipes or a minute is left before the nudge. The
service hears about swipes only, not about the person leaving the feed, so the bubble goes
away a few seconds after the last swipe.

Every swipe is added to today's total for its app, with the time since the previous swipe as
the time spent watching. The History tab shows the totals for today, 7 days, 30 days, 12 months,
all time or any dates chosen from a calendar. The counts live in a preferences file on the
phone and can be cleared there.

The character calls the person Nanba, Nanbi or a word of their own, and speaks Tamil typed in
English letters. His lines are in `res/values/strings.xml`.

## Build and run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
```

Open the app, tap **Put Vaathi on duty**, agree to the disclosure and switch it on in
Accessibility settings. On Android 13 and later, an APK installed outside Play or `adb` has
the switch greyed out until you open App info → ⋮ → **Allow restricted settings**.

**See Vaathi walk in** plays the nudge inside the app without any swiping.

To see what the service receives, in a debug build only:

```bash
adb shell setprop log.tag.ScrollWatch DEBUG
adb logcat -T 1 -s ScrollWatch:D
```

To play the nudge in its real window with no swiping, in a debug build with Vaathi on duty:

```bash
adb shell am broadcast -a com.scrollbuddy.DEBUG_NUDGE -p com.scrollbuddy
```

That is the way to try the window on an emulator, which has neither app. **See Vaathi walk in**
plays the same scene in a dialog, which is not the same window. The count bubble has a command
of its own, and `--ei left 2` turns it into its warning:

```bash
adb shell am broadcast -a com.scrollbuddy.DEBUG_COUNT -p com.scrollbuddy
```

Unit tests: `./gradlew :app:testDebugUnitTest`.

## The character

Vaathi is a generated cartoon image, animated by Kling's image-to-video tool into one
10-second take on a green backdrop: `art/clips/gestures.mp4`. `art/make_clips.py` cuts the
take into the clips the app plays, removes the green and writes them as animated WebP files in
`res/raw`. It also cuts the launcher icon's head from one frame, and cuts out the still of him
in sunglasses, `art/clips/shades.jpg`, which the first-run pages and the empty History tab
show.

| Clip | What he does | Where the app plays it |
|---|---|---|
| `wave` | Takes a hand out of his pocket and waves | As he slides in; on the Home tab |
| `stop` | Raises his hand into a stop sign, in a closer shot | As his line appears |
| `hold` | Holds the stop sign up; loops | While his line is on screen |
| `sweep` | Lays a flat hand on his shoulder and sweeps it out to his side | After **Close the app** |

To cut the clips again, with Blender 4.5 and ffmpeg installed:

```bash
/Applications/Blender.app/Contents/MacOS/Blender -b -P art/make_clips.py -- art/clips/gestures.mp4
```

Blender is there only for its Python, which has numpy and can write WebP. The frame numbers,
the column he stands on and the clip widths at the top of the script are read off this one
take, so a new take needs its own. The widths, and how much larger he is in the closer shot,
are repeated in `ui/Vaathi.kt` and must match.

The take was made on Kling's free plan and carried its watermark, which the cut leaves out.
Check that plan's terms before publishing the clips in an app.

## Releasing on Google Play

`./gradlew :app:bundleRelease` builds a minified bundle; it still needs a signing config and
your upload key. Play also asks for these before it accepts an accessibility service:

- A privacy policy URL. `PRIVACY.md` is a draft to host.
- The Accessibility API declaration in Play Console, with a short video of the disclosure
  dialog and of the nudge appearing. The app is not an accessibility tool
  (`isAccessibilityTool="false"`), so the declaration is "uses the API for another purpose".
- The in-app disclosure and agreement, which is the dialog behind **Put Vaathi on duty**.

Three things to settle before publishing:

- The character is a caricature of a real actor and the lines play on his film dialogues. Using
  a real person's likeness in a published app needs his permission, and Play can remove apps
  that suggest an endorsement that doesn't exist.
- The clips come from a video made on Kling's free plan; see "The character".
- Some payment apps refuse to run while an accessibility service they don't trust is on. Check
  a build installed from Play's internal testing track against the payment apps you care about.

## The early-access page

`web/early-access/` is a static page that introduces the app and collects email addresses for
early access. Its own README says how to preview it, connect the form and put it online.

## Where things are

| Path under `app/src/main/kotlin/com/scrollbuddy/` | What it does |
|---|---|
| `detector/ShortVideoFeed.kt` | Tells the Reels and Shorts pagers from every other scrolling view |
| `detector/SwipeCounter.kt` | Turns a pager's scroll events into swipes |
| `detector/DoomScrollDetector.kt` | Sessions, the two limits and reminders; pure Kotlin |
| `service/ScrollWatchService.kt` | The accessibility service; feeds events to the detector |
| `history/UsageHistory.kt`, `HistoryStore.kt` | Daily totals, the ranges on the History tab, and their storage |
| `overlay/NudgeOverlay.kt`, `NudgeScene.kt` | The full-screen window and the scene played in it |
| `overlay/CounterBubble.kt` | The small count shown over Reels and Shorts |
| `overlay/NudgeText.kt` | The character's lines, the button labels and the bubble's text |
| `ui/Vaathi.kt` | Plays the character's clips |
| `ui/OnboardingScreen.kt` | The three pages shown on first run |
| `ui/HomeScreen.kt`, `HistoryScreen.kt` | The two tabs |
| `ui/Rules.kt` | The limit and form-of-address pickers, shared by first run and Home |
| `ui/theme/Theme.kt` | Colours, shapes and type. The colours are taken from the character's shirt; the type is Roboto, carried in `res/font` (Apache License 2.0), because some phones ignore bold in their own system font |
| `settings/BuddySettings.kt` | The limit, tracked apps and the form of address |
