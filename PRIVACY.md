# Vaathi ride-uhh! privacy policy

Draft. Add your name or company, a contact address and the date before publishing it.

## The short version

The app collects nothing. It has no internet access, no account, no analytics and no
advertising. Everything it counts stays on your phone.

## The Accessibility API

The app uses Android's Accessibility API for one purpose: to notice when you open Instagram or
YouTube and when you swipe from one video to the next in Reels and Shorts. On top of those apps
it shows a small count of the day's swipes while you swipe, and its character when you reach
the limit you set.

- It receives two kinds of event, from Instagram and YouTube only: something scrolling, and one
  of their windows coming to the front. Android does not deliver events from any other app to
  it.
- It cannot read the content of your screen, what you type, or your passwords, in those apps or
  any other. It is not given that access.
- From each scroll event it uses the type of view that scrolled, its position and how far it
  moved. That is how it tells the Reels and Shorts players from the rest of each app.
- From each window event it uses the name of the screen and whether it fills the display. That
  is how it tells you coming back to the app from you moving around inside it, so that the
  count starts again at each visit.

## What is stored on your phone

- The number of swipes through Reels and Shorts, and the time between them, as a total for each
  app for each day.
- Your settings: the limit, which apps are watched, and how the character addresses you,
  including a name of your own if you typed one.

You can delete the totals at any time with **Clear history** on the History tab. Uninstalling
the app deletes everything. The data is not included in Android backups.

## What is shared

Nothing. The app does not request the internet permission, so it has no way to send data
anywhere, and it contains no third-party SDKs.

## Changes

If a future version changes any of this, the policy and the disclosure inside the app will be
updated before that version is released.
