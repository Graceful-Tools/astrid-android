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
| Settings | ✅ | 🟡 | AATD-13/55 | iOS's SettingsView order and pages (Reminders, Appearance, Language, Account & Access). Diverges: **Last Sync** row waits for the core to answer its last-sync time; **Language** is Android 13+ only (LocaleManager + `localeConfig`, no appcompat) and also appears in the system's per-app language settings; the Reminders "Open Settings" row shows whenever the system blocks this app's notifications; no iPad column options (Android lays out by width); **Contacts** (item 31) not built |
| Profile, Account & Access, Delete account | ✅ | 🟡 | AATD-56 | Profile card, stats, Edit Profile sheet (name + photo); Account & Access with verification, info, passkeys (rename, revoke), export via the share sheet, type-to-confirm delete. Diverges: **email** is read-only and there is no **Cancel Change** (the core cannot change the address yet); **Add a Passkey** opens the web's account page in a Custom Tab (the core leaves registration to the browser); the profile's "Your tasks" section is not built |
| Themes (Ocean, Light, Dark, Auto) | ✅ | ✅ | AATD-5 | Per device, as iOS's @AppStorage |
| Reminders | ✅ | ✅ | AATD-14/21 | Notifications with Complete and Snooze 1 h; one AlarmManager alarm at the core's `nextReminder` (iOS schedules each ahead) |
| List settings, sharing, members | ✅ | ✅ | AATD-15 | Permissions are the core's `can*` answers |
| Share extension | ✅ | ✅ | AATD-16 | `ACTION_SEND` → the add sheet |
| Home Screen quick action | ✅ | ✅ | AATD-16 | Launcher shortcut "New task" |
| Universal links | ✅ | 🟡 | AATD-16/19 | astrid.cc/t/… opens the task via a chooser until assetlinks.json is published for the release key |
| Chat | ✅ | ✅ | AATD-17 | |
| iPad two-column layout | ✅ | ⏳ | | Tablets and foldables get the phone layout today |
| Agent Hub, Connections | ✅ | 🟡 | AATD-27 | Lists and modes; the depth of iOS's hub is item 35 |
| Apple Reminders sync | ✅ | ➖ | | Apple framework |
| On-device Apple Foundation Models | ✅ | ➖ | | Apple framework; server agents are the cross-platform path |
| Sign in with Apple (native) | ✅ | ➖ | | The web has no Apple provider for the hand-off |
