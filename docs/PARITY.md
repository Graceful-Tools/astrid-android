# Parity with iOS

*What the Android app does against the iOS app. ✅ done · 🟡 partial · ⏳ planned · ➖ not applicable.
Diverge only for Android interaction or capability, and say why in Notes. Rewritten 2026-10-19 after the
parity pass (AATD-30…66, audit in [docs/audit](./audit)); checked on a device against the same account
as iOS's UI suite (`scripts/live-test.sh --tour`).*

## Screens and features

| What | iOS | Android | Task | Notes |
|---|---|---|---|---|
| Sign in | ✅ | ✅ | AATD-6/58 | iOS LoginView: white page, character beside the wordmark and slogan, buttons in the server's order (core `refreshCapabilities`), the real error. Every provider through the browser hand-off in a Custom Tab — Passkey opens the hand-off page rather than iOS's New/Returning sheet; Apple has no browser flow. Native Google sign-in (Credential Manager) is AATD-28 |
| Launch | ✅ | ✅ | AATD-34 | Android 12 launch screen with the character on Ocean, then iOS's SplashView look-alike while the session is checked |
| Use without account, first-run welcome | ✅ | ➖ | AATD-58, AWTD2-89 | astrid-core has no local (no-account) user; needs a core mode first |
| Sidebar | ✅ | ✅ | AATD-7/39 | Navigation drawer (Android's form of the iPhone overlay), ~85% wide: iOS 26's grouped cards — logo, wordmark and search; My Tasks with the filter glyph and count; Favorites; Lists with Add List first, list pictures (core `listImage`, iOS's hashed defaults, hidden by `features.showListImages`), privacy icons, iOS's counts (core `listBadges`); long press favourites; Public Shared Lists and Public Lists; the account card (Profile, gear to Settings) |
| List header | ✅ | ✅ | AATD-35 | Floating card, two-line menu icon, headline title (My Tasks: " - Today !!" suffix), the view rotator (list → messages → board) with next-mode icons, filter (My Tasks) or gear |
| Task rows | ✅ | ✅ | AATD-8/36 | iOS's checkbox artwork (repeat variants), photos with initials, list chips with iOS's glyphs, timed dates "Oct 19 6:00 PM" (core `dueAt`), Won't do / Syncing / repo#N title chips, card colours and metrics, 16dp indent (max 4), Copy task id on long press, selection in two panes. Android shows "Waiting to send" on a row still in the Outbox (iOS marks only GitHub-board syncing) |
| Swipe | ✅ | ✅ | AATD-8 | Left to delete, as iOS's only row swipe; a copy-only public task cannot be swiped |
| Manual order and nesting | ✅ | ✅ | AATD-42 | Press and hold, then drop on a row (subtask), on the line (promote; reorder when sorted by hand), or 40dp left (outdent) — core `nestTask`, iOS's DragNesting rules; TalkBack actions for each |
| Quick add | ✅ | ✅ | AATD-8/37 | Floating bar, priority/assignee control and sheet (core `quickAddDefaults`), growing field, plus creates and opens, Return creates in place; hidden where the reader may not add (core `canAddTasks`) |
| Empty and loading states | ✅ | ✅ | AATD-38 | Astrid's speech bubble with iOS's per-list messages |
| Sort and filters | ✅ | ✅ | AATD-41 | The core's groups for a list (List Settings → Filters, with Show subtasks and Saved Filter) and for My Tasks (header sheet); Clear All Filters |
| Search | ✅ | ✅ | AATD-11/47 | A mode of the main pane from the sidebar's magnifier, iOS's header and empty states, subtasks spliced, the checkbox completes |
| Board | ✅ | ✅ | AATD-12/20/45 | iOS's column count (one on a phone), one-column paging, framed columns with count and description, drag with ring and insertion line (the board follows the finger, so a card reaches a column that was off screen), per-column quick add, Astrid in an empty column, Move to… for TalkBack. Full screen on a wide window |
| Task detail | ✅ | ✅ | AATD-9/48–53 | "Task Details", iOS's icon column, value boxes and chips; Who (faces, You / AI Agent, Someone else by email, several people where the list allows — core `toggleAssignee`), When (date, time and repeat chips and sheets, All day, iOS's custom editor with the core-worded Preview), priority, lists, Task ID, project state, Waiting on, Occurrences, project fields, description, Attachments with a gallery, parent link, sub tasks, timer, completed via. Menu: Copy, Share, Won't do / Reopen, Delete task. Diverges: steppers instead of wheels; week of month 1st–5th (the core cannot read "last", AWTD2-93); the timer is the core's count-up stopwatch (iOS counts down and rings); non-image files open in another app (no Quick Look) |
| Comments | ✅ | ✅ | AATD-53 | Comments (n), Show/Hide system, chat bubbles with faces and name · time, markdown, replies, Copy / Reply / Edit / Delete, completion streaks (core `streaks`), @ # ! suggestions (core `commentSuggestions`), photo / document / camera queued and sent with the text (the text rides on the first file — the core attaches one file per comment), agent typing indicator (ends on the agent's reply), Offline |
| Chat | ✅ | ✅ | AATD-17/62 | In place of the rows through the rotator, on lists and on My Tasks (the reader's virtual channel, core 7450328); offline strip and typing indicator |
| List Settings | ✅ | ✅ | AATD-15/43 | Filters / Members / Admin tabs: information, image, colour (custom colour as hue/saturation/brightness sliders — Android has no system colour picker), task defaults, project board, recently completed, AI coding agent, external sync, Danger Zone. Pushed screens rather than iOS's sheets |
| Members and sharing | ✅ | ✅ | AATD-44 | Make Public / Make Private, owner row, members with avatars and a menu, invitations (core `listMembers`), Add Member with roles, Share List (core `shareList`), AI agents, Leave and Transfer ownership with confirmations. Tapping a member does not open their profile (no other-person profile screen, AATD-69); no contacts upload prompt |
| New list | ✅ | ✅ | AATD-40 | Name, description, members with roles. Android opens the new list afterwards (iOS dismisses) |
| Public lists | ✅ | ✅ | AATD-46 | Sidebar sections, the Featured Lists browser, and a featured list's tasks from the server, never cached, someone else's read-only (core `featuredRows`); Copy List |
| Settings | ✅ | ✅ | AATD-13/55 | iOS's order and pages: Sync (Last Sync, Sync Now), Reminders, AI Agents, Connections, GitHub Issues, Google Tasks, Appearance, Language, Smart Task Creation, Sub tasks, Outbox (dropped writes, retry), Account & Access, About, Sign Out. Language is Android 13+ (LocaleManager, also in the system's per-app languages); no iPad column options (layout follows width); Contacts not built (AATD-68) |
| Profile, Account & Access, Delete account | ✅ | 🟡 | AATD-56, AWTD2-90/94 | Profile card and stats, Edit Profile, verification, passkeys (rename, revoke), export through the share sheet, type-to-confirm delete. **Missing (needs the server and core):** changing the email address, adding a passkey natively (opens the web's account page), the profile's "Your tasks" |
| AI Agents, Connections | ✅ | 🟡 | AATD-27/57, AWTD2-92 | Ownership and transport, keys, Copilot connect, webhook server, Custom Agents, Astrid's model; Connections with revoke confirmation and the OAuth client editor. **Missing (no core command):** stored-key preview and validity, Copilot cloud-agent token, GitHub App status, Custom Agent photo; names are validated by the server, not as you type |
| GitHub Issues / Google Tasks | ✅ | 🟡 | AATD-57, AWTD2-91 | Connect, link a list, Google's mode and Sync now. **Missing (no core command):** the account-wide Linked lists, "Connected as", Google "New List" create-and-link |
| Reminders | ✅ | ✅ | AATD-14/21/54 | "Task Due Soon", Complete and Snooze 1 hour (Complete runs in the background — iOS opens the app), grouped; iOS's reminder card and the triage deck (core `reminderTriageDeck`) with Assign to Astrid; explain-first permission prompt with iOS's limits; exact alarms row. One AlarmManager alarm at the core's `nextReminder` (iOS schedules each ahead) |
| Share to Astrid | ✅ | ✅ | AATD-16/60 | One image or file (the first of several) into iOS's form. Shared text is an Android extra |
| Launcher shortcut "New task" | ➖ | ✅ | AATD-16 | Android extra; opens the share form empty |
| Deep links | ✅ | 🟡 | AATD-16/59 | iOS's set (`astrid://` and astrid.cc `/t` `/tasks` `/lists` `/settings/<page>` `/s/<code>` through core `resolveShortcode`). Via a chooser until assetlinks.json is published for the release key (AATD-19); `users/<id>` opens your own profile |
| Tablets | ✅ | ✅ | AATD-26/61 | By width: the detail pane beside the list when a task is picked (a second tap closes it), permanent sidebar from 840dp, messages pinned in wide landscape. The detail is a pane, not iOS's sliding overlay |
| Review prompt, feedback | ✅ | ✅ | AATD-63 | "Loving Astrid?" with iOS's rules → Play In-App Review; Help Us Improve; Send Feedback in Settings (Android addition). Report an Issue opens astrid.cc (the core does not name the Bugs & Feedback list) |
| "A new Astrid is ready" card | ✅ | ⏳ | AATD-67 | Needs an app-version check (or Play In-App Updates) |
| Themes | ✅ | ✅ | AATD-5/33 | Ocean, Light, Dark, Auto per device; every Material role and the system bars follow the theme |
| Offline, live updates, background refresh | ✅ | ✅ | AATD-10/30 | The core's Outbox and change stream (listened to by its own words since AATD-30); WorkManager every 15 min (iOS: BGTask) |
| Languages | ✅ | ✅ | AATD-23/65 | The 12 iOS languages: iOS's own translations where the English matches (`scripts/port-ios-strings.py`), the rest in `strings_extra.xml` |
| Accessibility | ✅ | ✅ | AATD-18/64 | Every tappable control labelled — a device test walks list, sidebar, detail, search, settings, profile and list settings |
| Apple Reminders sync | ✅ | ➖ | | Apple framework |
| On-device Apple Foundation Models | ✅ | ➖ | | Apple framework; server agents are the cross-platform path |
| Sign in with Apple (native) | ✅ | ➖ | | The web has no Apple provider for the hand-off |

## How this is checked

- `npm run predeploy` — core, bindings, unit tests (incl. Robolectric), lint, debug APK.
- `npm run predeploy:full` — plus the offline device suite on an emulator.
- `scripts/live-test.sh` — the device suite signed in as `uitest@astrid.cc` against astrid.cc (the iOS UI
  suite's account); `--tour` saves a screenshot of every main screen to set beside iOS's.
