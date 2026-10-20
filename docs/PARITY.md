# Parity with iOS

*What the Android app does against the iOS app. ✅ done · 🟡 partial · ⏳ planned · ➖ not applicable.
Diverge only for Android interaction or capability, and say why in Notes. Updated 2026-10-18.*

| What | iOS | Android | Task | Notes |
|---|---|---|---|---|
| Sign in | ✅ | ✅ | AATD-6/58 | iOS LoginView's layout: character, server wordmark and slogan, buttons in the order `/api/v1/capabilities` lists them (core `refreshCapabilities`), the real error. Browser hand-off in a Custom Tab for every provider — Passkey opens the hand-off page rather than iOS's New/Returning sheet; Apple is not offered (no native flow). Native Google sign-in through Credential Manager is later polish |
| Use without account, first-run welcome | ✅ | ➖ | AATD-58 | Not built: astrid-core has no local (no-account) user. Needs a core mode first |
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
| Settings | ✅ | 🟡 | AATD-13/55 | iOS's SettingsView order and pages (Reminders, Appearance, Language, Account & Access). Diverges: **Last Sync** row waits for the core to answer its last-sync time; **Language** is Android 13+ only (LocaleManager + `localeConfig`, no appcompat) and also appears in the system's per-app language settings; the Reminders "Open Settings" row shows whenever the system blocks this app's notifications; no iPad column options (Android lays out by width); **Contacts** (item 31) not built |
| Profile, Account & Access, Delete account | ✅ | 🟡 | AATD-56 | Profile card, stats, Edit Profile sheet (name + photo); Account & Access with verification, info, passkeys (rename, revoke), export via the share sheet, type-to-confirm delete. Diverges: **email** is read-only and there is no **Cancel Change** (the core cannot change the address yet); **Add a Passkey** opens the web's account page in a Custom Tab (the core leaves registration to the browser); the profile's "Your tasks" section is not built |
| Themes (Ocean, Light, Dark, Auto) | ✅ | ✅ | AATD-5 | Per device, as iOS's @AppStorage |
| Reminders | ✅ | ✅ | AATD-14/21/54 | "Task Due Soon" with the title beneath; Complete and Snooze 1 h with icons — Complete runs in the background (iOS opens the app). Several due fold under a group summary. Tapping opens iOS's reminder card (Astrid's phrase, who is counting on you, Complete, Snooze 15 min / 1 day / 1 week) or, from the summary, the triage deck of those reminders (complete, tomorrow, keep). iOS deals the deck from every task of yours due today or overdue and offers "Assign to Astrid" — both need a core command. One AlarmManager alarm at the core's `nextReminder`; exact alarms through Settings → Alarms & reminders (a Settings row while not allowed). Permission: iOS's explain-first alert after sign-in and after a due date is set, three times then monthly, Open Settings once refused |
| List settings, sharing, members | ✅ | ✅ | AATD-15 | Permissions are the core's `can*` answers |
| Share extension | ✅ | ✅ | AATD-16/60 | `ACTION_SEND` (one image or file; the first of `SEND_MULTIPLE`) → iOS's form: title from the file name, description, priority, attachment, created in your default list and assigned to you, the file attached through the core. Shared text is an Android extra (iOS takes files only) |
| Home Screen quick action | ➖ | ✅ | AATD-16/60 | Android extra — iOS has none. The launcher shortcut "New task" opens the share form empty |
| Universal links, deep links | ✅ | 🟡 | AATD-16/19/59 | iOS's set: `astrid://tasks/`, `lists/`, `users/`, `settings/`, `s/` (and Android's older `astrid://task/`), astrid.cc `/t/`, `/tasks/`, `/lists/`, `/settings/<page>`, `/s/<code>`; other pages open in a Custom Tab. Settings pages without an Android screen open Settings; `users/<id>` opens your own profile. `/s/` asks the core's `resolveShortcode` (not in the core yet — falls back to the web, as iOS does on failure). Links go via a chooser until assetlinks.json is published for the release key |
| Chat | ✅ | ✅ | AATD-17 | |
| iPad two-column layout | ✅ | ⏳ | | Tablets and foldables get the phone layout today |
| Agent Hub, Connections | ✅ | 🟡 | AATD-27 | Lists and modes; the depth of iOS's hub is item 35 |
| Agent Hub, Connections, Profile stats, data export | ✅ | ⏳ | | The core has every command; screens not built |
| AI Agents hub | ✅ | 🟡 | AATD-57 | Ownership segmented control, transport menu, key editor, Copilot connect (Custom Tab, status polled on return), webhook server, Custom Agents, Astrid's model. Rows are the core's `agents` (server order and names, incl. a locked Codex row) rather than iOS's fixed five. **Not built (no core command):** stored-key preview and validity, Copilot cloud-agent token screen, GitHub App connected/repository count, Custom Agent photo. Custom Agent names are validated by the server, not as you type |
| Connections | ✅ | ✅ | AATD-57 | The core's sections and owner badges; Revoke confirmed in an AlertDialog (iOS confirmationDialog); New connection / Edit in a bottom sheet. No pull-to-refresh |
| GitHub Issues / Google Tasks sync | ✅ | 🟡 | AATD-57 | Connect in a Custom Tab, link a list, Google sync mode and Sync now. **Not built (no core command):** the account-wide "Linked lists" with Unlink (Android shows the links made on the screen; per-list Unlink lives in list settings), "Connected as", and Google "New List" create-and-link. GitHub has no Sync now: the core says a server cron syncs it |
| Apple Reminders sync | ✅ | ➖ | | Apple framework |
| On-device Apple Foundation Models | ✅ | ➖ | | Apple framework; server agents are the cross-platform path |
| Sign in with Apple (native) | ✅ | ➖ | | The web has no Apple provider for the hand-off |
