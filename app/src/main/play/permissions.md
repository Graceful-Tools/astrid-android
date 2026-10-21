# Permissions — what Play Console's declarations say

Draft (AATD-19). `PlayListingTest` fails if the manifest asks for a permission this file does not explain.

| Permission | Why Astrid asks |
|---|---|
| `INTERNET` | Sync tasks, lists and comments with the Astrid server. |
| `ACCESS_NETWORK_STATE` | Show the offline strip and retry the queued changes when the connection returns. |
| `POST_NOTIFICATIONS` | Task reminders. Asked at runtime (Android 13+); refusing only turns reminders off. |
| `SCHEDULE_EXACT_ALARM` | A reminder fires at the time the user set. Not granted by default from Android 14; without it the reminder is inexact (`setAndAllowWhileIdle`), never lost. If Play's *Exact alarm* declaration asks, the answer is "Calendar / reminders: user-set task reminders". |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule pending reminders after the phone restarts. |
