# Habitude roadmap: October 2026 review

This plan follows a re-review of `main` on 18 October 2026 (after PR #17, app version 1.1.x, database version 7). It replaces the open part of the 8 October review. Every step also has a GitHub issue, listed in the tracking issue #18.

## Where things stand

The app is in good shape. Every bug from the first review is fixed, and the later work on release signing, data safety, the app lock and past days closed most of the remaining risks. Release APKs are signed with the release key, the database schema is exported with upgrade tests, and imports keep a safety copy first.

The re-review found two bugs worth fixing before anything else: tapping Save or Back twice can add a habit twice or leave a blank screen, and on some phones an import may refuse to run at all. Neither loses data. The rest is the open feature work and some polish.

How this was checked: every source file on `main` was read. The Android SDK can't be installed in the review sandbox, so the app was not built or run here. GitHub Actions builds it and runs the unit tests on every pull request. Items marked **needs check** should be tried on a phone.

### Done since the first review

| Item | Done in |
|---|---|
| 1–10, 12, 14–20 from the first review | PRs #6 to #10 |
| 21. Room schema export and database upgrade tests | PR #14 |
| 22. Version number set by the release build | PR #12 (release builds only; local and debug builds stay 1.1.0) |
| 23. Tests for the midnight case | Mostly in PR #16 (Today resets after midnight); see N6 for the rest |

## Findings

| ID | Priority | What goes wrong for you | Size |
|---|---|---|---|
| N1 | P1 | Tapping **Start this habit** or **Save** twice quickly saves the habit twice (a duplicate appears on Today). A quick double tap on a Back arrow, or on Save, can leave a blank screen with only the tab bar. | S |
| N2 | P1, needs check | **Import may never work on some phones.** Before importing, the app saves a safety copy and then checks the copy's timestamp to the millisecond. Some Android versions only store file times to the second, so the check fails and every import stops with "Couldn't save your previous data". | S |
| 13 | P2 | The evening streak check always comes at 8pm. People who train late or go to bed early can't move it. | S |
| 11 | P2 | Habits show in the order they were made. There's no way to put the most important ones first. | M |
| N3 | P2 | With TalkBack, the day picker reads "T" for both Tuesday and Thursday and "S" for Saturday and Sunday, and neither the days nor the icons say whether they're selected. | S |
| N4 | P3 | After midnight the home-screen widget can keep showing yesterday's ticks for up to 30 minutes, because it only refreshes every half hour unless a habit changes. | S |
| H10 | P3 | A count habit doesn't remember its old daily goals. After changing 8 glasses to 10, an old day shows "8 of 10", and changing a past day's count is judged against today's goal. Past days that were done stay done (stopgap from PR #16). | M |
| N6 | P3 | No test covers the shared clock that moves "today" forward when the app comes back or at midnight, or the widget and reminders around midnight. | S |
| N5 | P3, only for Google Play | Google Play has required new apps and updates to target Android 16 (API 36) since 31 August 2026. Habitude targets API 35. This only matters if you publish on the Play Store; sideloaded APKs keep working. | M |

## Pull request plan

One pull request per step, merged in this order. Each one is small and can be undone by reverting it.

1. **Double taps and import safety copy** (N1, N2), issue #19. Save buttons ignore a second tap while saving; Back, Save and Delete only navigate once. The safety copy keeps its time to the whole second, so the check works on every phone. Also adds this plan. Test: tap Start this habit twice fast, and check there's one habit. Import a backup with habits already in the app.
2. **Choose the evening check time** (13), issue #20. A time row under the evening check switch in Settings. The time is kept in backups. No database change. Test: set it to 2 minutes from now with a streak at risk, and the notification comes then.
3. **Reorder habits** (11), issue #21. The order is kept in the app's settings, so there is no database change. A "Reorder habits" button on Progress shows up and down buttons (works with TalkBack, unlike drag and drop). Today, Progress, the widget and backups follow the order. Test: move a habit to the top, and it's first on Today and the widget; export and import, and the order stays.
4. **TalkBack labels on the habit form, and the widget at midnight** (N3, N4, N6), issue #22. Full day names and selected states on the day and icon pickers. The widget refreshes just after midnight. Tests for the shared clock. Test: with TalkBack on, the day picker reads "Tuesday, selected".
5. **Count goal history** (H10), issue #23. Optional. Database version 8 keeps old daily goals, the same way schedules are kept, so old days are shown and edited against the goal they had.
6. **Target Android 16** (N5), issue #24. Optional, only for publishing on Google Play. Upgrades the build tools; test the whole app on a phone afterwards.

Only step 5 changes the database. Every step stands alone.

## Rules for every pull request

- One pull request per issue, branched from the latest `main`, under about 300 changed lines.
- No database, package name or signing changes unless the issue says so; a database change comes with a migration and an upgrade test.
- Keep CI green (unit tests, lint and the debug build) and add a test for logic changes.
- Never commit secrets, keystores or build output. The owner merges.
