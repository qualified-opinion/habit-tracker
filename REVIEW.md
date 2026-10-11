# Habitude review — October 2026

Read-only review of `qualified-opinion/habit-tracker` at `main` `90ead5f` (tag `v1.1.1`), on branch `review/habitude-2026-10`. No app code was changed. This file is the only change on the branch.

- Engine: Codex CLI (`gpt-6.1-sol`, high) run with `-s read-only`. Nightshift re-checked every finding below against the source. Codex findings that didn't hold up, or that were too minor, were cut.
- Kiln vets this list, then Jan picks the PR batches. Nothing gets fixed before that.

## Baseline

`./gradlew --no-daemon --rerun-tasks testDebugUnitTest lintDebug assembleDebug` (JDK 17, AGP 8.7.3, Gradle 8.11.1): **BUILD SUCCESSFUL**, 54/54 tasks run.

| Check | Result |
|---|---|
| Unit tests | **78 passed**, 0 failed, 0 skipped (11 test classes, all JVM. There are no instrumented tests.) |
| Lint | **0 errors, 56 warnings.** That is 22 unique issues, because the 17 dependency/AGP version warnings are each listed 3 times: 16 × `GradleDependency`, 1 × `AndroidGradlePluginVersion`, 1 × `OldTargetApi` (targetSdk 35), 1 × `DataExtractionRules` (`allowBackup` is deprecated), 2 × `UnusedAttribute` (widget `targetCell*` on minSdk 26), 1 × `ObsoleteSdkInt` (`mipmap-anydpi-v26`) |
| Debug APK | built (`app-debug.apk`, about 20 MB) |

## What checked out

- Room: there's no `fallbackToDestructiveMigration`. Migrations run 1→7 with no gaps, and the hand-written DDL matches the schema Room generates for v7 (checked against `HabitudeDatabase_Impl`).
- Backup export and import cover all five tables and every field. Parsing finishes before anything is deleted, and the replace runs in one `withTransaction`. Unknown keys are ignored and newer formats are rejected.
- PIN: random 16-byte salt, PBKDF2-HMAC-SHA256 with 60k iterations, constant-time compare. Wrong attempts and the cooldown are saved, so restarting the app doesn't reset them. Biometric is an extra way in, and the PIN always works.
- The 1-minute relock uses `elapsedRealtime`, so it's immune to clock changes. Recents are hidden while a PIN is set (`FLAG_SECURE` below API 33, `setRecentsScreenshotEnabled(false)` on 33+).
- Reminders are deliberately inexact (`setAndAllowWhileIdle`), so Android 14 exact-alarm permission doesn't apply. They are re-armed on BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET and TIMEZONE_CHANGED. POST_NOTIFICATIONS is declared, checked and requested.
- There's no `allowMainThreadQueries` or `runBlocking`. Dark mode has a full dark colour scheme, and the widget follows it.

## Findings (ranked)

Severity: **blocker** means fix before anyone installs the next update. **should-fix** means a real defect or risk. **nice-to-have** means minor or an edge case. Size: S < half a day, M ≈ 1 day, L = several days.

| ID | Sev | Where | Problem | Fix | Size |
|---|---|---|---|---|---|
| H01 | blocker | `README.md:31-33` | Releases up to v1.1.0 were debug-signed (`release.yml` ran `assembleDebug` until #12). v1.1.1 is release-signed, so it won't install over an older build. The README still says every build uses `app/debug.keystore` and updates keep data. Uninstalling wipes everything, because `allowBackup=false` | Rewrite the README and the v1.1.1 release notes: export, uninstall, install v1.1.1, import. Say that CI debug APKs and releases can't be installed over each other. Keep `app/debug.keystore`, but label it debug-only | S |
| H02 | should-fix | `ui/screens/SettingsScreen.kt:406` | Import replaces all data and keeps no safety copy, so picking the wrong (valid) backup loses everything for good | Write the current data to an app-private `pre-import-<date>.json` (or the auto-backup folder) before `replaceAll`, and offer "Undo import" | M |
| H03 | should-fix | `backup/AutoBackup.kt:101` | A second backup on the same day deletes the existing file before the new one is created and written. If the write fails, today's good backup is gone and a partial file is left that counts toward the 4 kept | Write to a temporary name first. Delete the old file only after the write succeeds, and delete the partial file on failure | S |
| H04 | should-fix | `backup/AutoBackup.kt:112` | Every failure returns `Result.failure()`, so a temporary provider or I/O error isn't retried. The next try is a week later | Return `Result.retry()` for temporary errors. Return `failure()` only when the folder or permission is gone | S |
| H05 | should-fix | `data/HabitudeDatabase.kt:13` | `exportSchema = false`, with no `room.schemaLocation` and no migration tests. The migrations are correct today, but nothing protects v8 (which H10 and H11 need) | Turn on schema export and commit `7.json`. Add `MigrationTestHelper` androidTests for 1..6→7 | M |
| H06 | should-fix | `MainActivity.kt:65`, `ui/edit/EditHabitScreen.kt:282` | On relock, open dialogs stay on top of the PIN screen, because Compose dialogs are separate windows. A delete confirmation, note dialog (`NoteDialog.kt:31`) or import confirmation left open can be used without the PIN | Don't show the app's dialogs while locked: gate them on lock state, or clear dialog state when relocking | M |
| H07 | should-fix | `notify/Notifications.kt:89` | With the PIN on, reminders and the 8pm check show habit names and streaks on the lock screen. There's no `setVisibility` or public version | When a PIN is set, use `VISIBILITY_PRIVATE` with a generic `setPublicVersion` ("A habit reminder") | S |
| H08 | should-fix | `widget/HabitWidget.kt:156`, `:81` | The widget shows habit names and allows ticks no matter what the PIN is set to | When a PIN is set, show "Locked – open Habitude" or counts only (a setting), and send taps to the app | M |
| H09 | should-fix | `ui/lock/LockScreen.kt:70` | The wrong-PIN cooldown uses the wall clock, so moving the clock forward skips it. That turns the 8-minute wait into unlimited guesses | Store an `elapsedRealtime` deadline plus the boot count. After a reboot, restart the full wait | S |
| H10 | should-fix | `domain/Habit.kt:32`, `domain/Streaks.kt:29` | A times-a-week goal has no history. Changing 3→5/week, or switching between set days and weekly, re-scores every past week, including streaks, best and milestones. Schedule changes don't work this way: they apply from today | Store goal history the way `past_schedules` stores schedules (DB v8 plus backup format 2), and score each week against the goal it had | L |
| H11 | should-fix | `data/HabitRepository.kt:88` | Editing a past day's count uses today's goal. After raising 8→10/day, tapping +1 on an old 8/8 day un-marks it | Use the goal in force that day (same history as H10). As a stopgap, leave a past day's done state alone unless the count crosses its old goal | M |
| H12 | should-fix | `widget/HabitWidget.kt:65`, `:85`, `res/xml/habit_widget_info.xml:12` | Nothing refreshes the widget at midnight. It can show yesterday for up to 30 minutes while a tap writes to today | Schedule a midnight `updateAll` (alarm or OneTimeWork), and pass the rendered day in the action parameters | M |
| H13 | should-fix | `ui/charts/CalendarHeatmap.kt:67`, `ui/charts/WeeklyBars.kt:56` | Days and weeks can only be picked by tapping the canvas, so TalkBack and switch users can't select a past day to backfill it or add a note | Add semantics nodes or custom actions for each cell, or an accessible "pick a day" button | M |
| H14 | should-fix | `ui/edit/EditHabitScreen.kt:204` | The weekday toggles read "T"/"S" (Tue and Thu, Sat and Sun sound the same), and selection is shown by colour only | Give each toggle its full day name as content description, plus `toggleable`/`selected` semantics | S |
| H15 | nice-to-have | `backup/AutoBackup.kt:74` | When folder access is lost (folder deleted or permission revoked), only a flag is set, shown in Settings. Weekly backups stop without any notice | Post a notification on the first failure, and check `persistedUriPermissions` at app start | S |
| H16 | nice-to-have | `MainActivity.kt:56-57` | Changing the time or timezone while the app is open doesn't refresh `DayClock`, and the midnight countdown keeps its old length | Listen for TIME_SET, TIMEZONE_CHANGED and DATE_CHANGED while started, then refresh and restart the loop | S |
| H17 | nice-to-have | `ui/edit/EditHabitViewModel.kt:35` | A new habit's start date is set when the form opens. Saving after midnight starts it yesterday, which then shows as a missed day in the 30-day rate | Set `createdOn` at save time for new build habits | S |
| H18 | nice-to-have | `domain/Stats.kt:119`, `ui/detail/HabitDetailScreen.kt:154` | After "clean since" is moved past old slips, those slips are still counted in the weekly slip bars and the 30-day slip count, though the heatmap and runs hide them | Filter out slips before `createdOn` everywhere | S |
| H19 | nice-to-have | `notify/ReminderScheduler.kt:46` | There's one `setAndAllowWhileIdle` alarm per habit. In Doze, Android allows about one of these per app every 9 minutes, so reminders set for the same time (and the 8pm check) can arrive spread out (per AlarmManager docs, not tested on a device) | Set one alarm per distinct time that posts every reminder due then | M |
| H20 | nice-to-have | `notify/Notifications.kt:68` | "Done ✓" and "+1" change data from the lock screen without the app PIN or device unlock | When a PIN is set, call `setAuthenticationRequired(true)` on the action (API 31+) | S |
| H21 | nice-to-have | `ui/lock/LockScreen.kt:51` | `checking` is `rememberSaveable`. Rotating during the PBKDF2 check cancels the check but restores `checking=true`, so the keypad stays disabled until the app restarts | Use plain `remember`, or move the check into a ViewModel | S |
| H22 | nice-to-have | `data/HabitRepository.kt:20`, `ui/today/TodayViewModel.kt:60` | Every write re-reads all 5 tables and recalculates current and best streaks for every habit, on the main thread (no `flowOn`). That's fine now, but it grows with years of history | Add `.flowOn(Dispatchers.Default)` to the derived flows. Later, query per habit | S |
| H23 | nice-to-have | `ui/lock/PinPad.kt:129`, `ui/lock/LockScreen.kt:84` | The keypad needs about 330dp (72dp keys) and doesn't scroll, so in landscape or at large font sizes the lock screen gets clipped and the PIN can't be entered | Make the lock layout scroll, or shrink the keys to fit the height | S |
| H24 | nice-to-have | `gradle/libs.versions.toml:2-16`, `app/build.gradle.kts:16` | Dependencies are about two years old: Room 2.6.1→2.8.5, Compose BOM 2024.12.01→2026.09.00, Work 2.10.0→2.12.0, Glance 1.1.1→1.2.0, AGP 8.7.3→9.4.1, Kotlin 2.0.21→2.4.21. targetSdk is 35 | Upgrade in stages after H05's migration tests: Kotlin/KSP/AGP first, then AndroidX, then targetSdk | M |

Counts: **1 blocker, 13 should-fix, 10 nice-to-have** (24 total).

## Proposed PR batches

1. **Data safety** (do first): H01, H02, H03, H04, H05, H15
2. **App lock and privacy**: H06, H07, H08, H09, H20, H21
3. **Date correctness** (needs DB v8, so after batch 1's schema export): H10, H11, H16, H17, H18
4. **Widget**: H12
5. **Reminders**: H19
6. **Accessibility and performance**: H13, H14, H22, H23
7. **Dependencies** (after batch 1's migration tests): H24

## Missing test cases

JVM unit tests (domain):
- Weekly goal raised or lowered mid-history; switching between set days and weekly (H10). Week streaks across the new year and ISO week 53.
- Count goal changed, then a past day edited (H11). Count habit switched back to a single tick.
- `nextTrigger` for a reminder time inside the spring-forward gap and in the repeated fall-back hour. `millisUntilTomorrow` on spring-forward (only fall-back is tested) and in a zone whose midnight is skipped.
- Moving "clean since" past existing slips: `Stats.weekly` and the 30-day count (H18). Slips before the start date. Consecutive slips.
- A milestone run that breaks and regrows between checks. An import that reuses ids of habits already celebrated.
- Backups: one file with every optional field set at once, old v1 fixtures, invalid dates in counts, notes or past schedules, and non-positive format numbers.
- `Pin.lockoutSeconds` cap at 8 failures and beyond. Cooldown after the clock changes (H09).

Instrumented or Robolectric tests (none exist today):
- Room `MigrationTestHelper`: every path 1..6→7, keeping every field and the foreign keys (H05).
- `replaceAll` rolls back when an insert fails partway. Pre-import safety copy (H02).
- Auto-backup: SAF permission revoked, provider returns null, write interrupted, delete returns false, worker retry, and keeping exactly 4 files with 0, 4 and 5 present (H03, H04).
- `BootReceiver` reschedules on each of the 4 broadcasts. A notification action tapped after midnight ticks the reminder's day.
- Relock with the delete, note or import dialog open (H06). Rotation during the PIN check (H21).
- Widget at midnight and with a PIN set (H08, H12).
- TalkBack: selecting a heatmap day, weekday toggle announcements (H13, H14).

## Out of scope

- No app code changed, no PR opened, no merges, tags, releases, secrets or settings touched. This branch only adds `REVIEW.md`.
- Not covered: release workflow secrets and signing setup (beyond the README impact in H01), Play Store policy, UI copy and visual design, translations.
- Not tested on a device or emulator. Doze timing (H19) and the dialog-over-lock behaviour (H06) were reasoned from source and platform docs. Reproduce them on a device before or while fixing.
