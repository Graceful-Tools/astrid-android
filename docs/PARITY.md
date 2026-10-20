# Parity with iOS

*What the Android app does against the iOS app. ✅ done · 🟡 partial · ⏳ planned · ➖ not applicable.
Diverge only for Android interaction or capability, and say why in Notes. Updated 2026-10-18.*

| What | iOS | Android | Task | Notes |
|---|---|---|---|---|
| Sign in | ✅ | ✅ | AATD-6 | Browser hand-off in a Custom Tab (Google, Passkey, GitHub, SSO); "Astrid for Android" on the page. Native Google sign-in through Credential Manager is later polish |
| Sidebar: lists, favourites, virtual lists | ✅ | ✅ | AATD-7 | Navigation drawer — Android's form of the iPhone overlay sidebar |
| My Tasks | ✅ | ✅ | AATD-7/8 | Opens by default; a task added there is assigned to you (the core's quick-add default) |
| Task list with subtasks | ✅ | ✅ | AATD-8 | Rows are the core's projection, drawn to iOS's TaskRowView metrics |
| Quick add with smart parsing | ✅ | ✅ | AATD-8 | The reader's locale picks the keyword language |
| Completion with repeat rollover | ✅ | ✅ | AATD-8 | `completeTask` only; the leading control does what the core's `action` says |
| Swipe actions | ✅ | ✅ | AATD-8 | Right to complete, left to delete |
| Task detail | ✅ | 🟡 | AATD-9 | Every field in the core's order; Material date picker. **Custom repeat patterns** and a free time-of-day picker are not built yet (presets and the core's quick hours are) |
| Comments, attachments | ✅ | ✅ | AATD-9/17 | Attach from the system picker; open through a FileProvider. Inline image previews are polish |
| Offline with the Outbox | ✅ | ✅ | AATD-10 | Offline is never an error; rows say "Waiting to send" |
| Live updates | ✅ | ✅ | AATD-10 | One change stream from the core |
| Background refresh | ✅ | ✅ | AATD-10 | WorkManager every 15 min (iOS: BGTask) |
| Search | ✅ | ✅ | AATD-11 | The web grammar, over the cache |
| Board view | ✅ | 🟡 | AATD-12 | Columns, status picker, "Move to…" (long press and TalkBack). **Drag-and-drop** is AATD-20 |
| Settings | ✅ | ✅ | AATD-13 | Account, reminders, tasks, appearance, about, sign out |
| Themes (Ocean, Light, Dark, Auto) | ✅ | ✅ | AATD-5 | Per device, as iOS's @AppStorage |
| Reminders | ✅ | ✅ | AATD-14/21 | Notifications with Complete and Snooze 1 h; one AlarmManager alarm at the core's `nextReminder` (iOS schedules each ahead) |
| List settings, sharing, members | ✅ | ✅ | AATD-15 | Permissions are the core's `can*` answers |
| Share extension | ✅ | ✅ | AATD-16 | `ACTION_SEND` → the add sheet |
| Home Screen quick action | ✅ | ✅ | AATD-16 | Launcher shortcut "New task" |
| Universal links | ✅ | 🟡 | AATD-16/19 | astrid.cc/t/… opens the task via a chooser until assetlinks.json is published for the release key |
| Chat | ✅ | ✅ | AATD-17 | |
| iPad two-column layout | ✅ | ⏳ | | Tablets and foldables get the phone layout today |
| Agent Hub, Connections, Profile stats, data export | ✅ | ⏳ | | The core has every command; screens not built |
| AI Agents hub | ✅ | 🟡 | AATD-57 | Ownership segmented control, transport menu, key editor, Copilot connect (Custom Tab, status polled on return), webhook server, Custom Agents, Astrid's model. Rows are the core's `agents` (server order and names, incl. a locked Codex row) rather than iOS's fixed five. **Not built (no core command):** stored-key preview and validity, Copilot cloud-agent token screen, GitHub App connected/repository count, Custom Agent photo. Custom Agent names are validated by the server, not as you type |
| Connections | ✅ | ✅ | AATD-57 | The core's sections and owner badges; Revoke confirmed in an AlertDialog (iOS confirmationDialog); New connection / Edit in a bottom sheet. No pull-to-refresh |
| GitHub Issues / Google Tasks sync | ✅ | 🟡 | AATD-57 | Connect in a Custom Tab, link a list, Google sync mode and Sync now. **Not built (no core command):** the account-wide "Linked lists" with Unlink (Android shows the links made on the screen; per-list Unlink lives in list settings), "Connected as", and Google "New List" create-and-link. GitHub has no Sync now: the core says a server cron syncs it |
| Apple Reminders sync | ✅ | ➖ | | Apple framework |
| On-device Apple Foundation Models | ✅ | ➖ | | Apple framework; server agents are the cross-platform path |
| Sign in with Apple (native) | ✅ | ➖ | | The web has no Apple provider for the hand-off |
