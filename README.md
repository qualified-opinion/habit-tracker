# Habitude

A small, good-looking Android app for building the habits you want and quitting the ones you don't. Streaks, progress charts and gentle reminders, all stored on your phone.

## What's here so far

- Kotlin + Jetpack Compose app with a soft sage-and-clay Material 3 theme (light and dark)
- Three tabs: **Today**, **Progress** and **Settings**
- **Build** habits: tick them off on their scheduled days to grow a 🔥 streak; rest days don't break it
- Or give a build habit a **times a week** goal (say, gym 3 times a week on any days); its streak counts the weeks you hit it
- **Count habits**, like 8 glasses of water: tap the ring on Today once each time, and the day counts as done when it reaches the goal
- **Home-screen widget**: today's habits with a tap to tick each one off (or count one more)
- **Milestones**: a small celebration when a streak or clean run reaches 7, 30, 100 or 365 days (4, 12, 26 or 52 weeks for a times-a-week goal), and the ones reached on each habit's stats page
- **Break** habits: count your days clean, and log a slip honestly when it happens. Set a "clean since" date if you quit before you started using the app
- **Today** shows how many of today's habits are done, with the ones still to do at the top
- **Reorder habits** from the Progress tab (Your habits → Reorder habits); Today, Progress and the widget follow your order
- **Archive** a habit you're pausing: it keeps its history, leaves Today and stops its reminders, and can be restored from its edit screen
- Current and best streak on every habit, all stored on the phone (no account)
- Tap a habit for its stats: a 20-week calendar heatmap, weekly completion (or slips) bars and streak history
- Forgot to log a day? Tap it on the habit's heatmap to mark it done (or log or remove a slip)
- **Notes** on any day: what led to a slip (asked for right when you log one), or how a day went. They're listed on each habit's stats page, to help spot triggers
- Changing a habit's days applies from today on, so past streaks stay as they were
- **Progress** tab: today at a glance, an all-habits heatmap and a 30-day rate for each habit
- **Reminders**: an optional daily reminder per habit, with a "Done ✓" button right on the notification
- **App lock**: an optional 6-digit PIN, asked for when you open the app or come back after a minute away. On Android 9 and later you can unlock with your fingerprint or face too
- **Backup**: export everything to a JSON file and import it again on a new phone or after reinstalling (Settings → Backup). Or turn on a **weekly backup** to a folder you pick, which keeps the newest four. Android's own cloud backup is off, so nothing leaves the phone unless you choose where it goes
- **Evening streak check** at 8pm, or a time you pick in Settings, if a streak of two days or more is about to break (can be turned off)
- GitHub Actions: every push and pull request runs the unit tests and builds a debug APK for testing; publishing a release builds the release-signed APK and attaches it to that release

## Get the APK

Download `habitude-<version>.apk` from the latest release under **Releases** and install it on your phone (you may need to allow installs from unknown sources). Release APKs are signed with the Habitude release key.

The `habitude-debug-apk` artifact under **Actions → Android build** is a debug build for testing only. It's signed with a different key, so it can't be installed over a release APK (or the other way round) without uninstalling first.

### Upgrading from v1.1.0 or earlier

Releases up to and including v1.1.0 were signed with the debug key. From v1.1.1 on, releases are signed with the release key. Android won't install an app over one signed with a different key, so moving from an older build needs a one-time reinstall, and uninstalling deletes the app's data. To keep your habits:

1. In the old app, export a backup (Settings → Backup) and keep the JSON file somewhere outside the app.
2. Uninstall the old app.
3. Install the new APK.
4. Import the backup (Settings → Backup).

After that, future release updates install in place over the previous one and keep your data.

## Build locally

Needs JDK 17 and the Android SDK (Android Studio sets both up).

```sh
./gradlew assembleDebug
```

Debug builds are signed with `app/debug.keystore`, a fixed key kept in the repo for debug builds only. It is never used to sign releases.

## Roadmap

1. App skeleton and theme ✅
2. Habits to build and break, with streaks ✅
3. Charts and progress ✅
4. Reminders and polish ✅
