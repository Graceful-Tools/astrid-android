# iOS parity audit (2026-10-19): Design system, settings and account, platform

*Read-only comparison of astrid-ios and astrid-android source. Each finding names the iOS evidence, the Android evidence, and what Android must change. Work is tracked as AATD tasks that cite these item numbers.*

I found 60 inconsistencies. Android has the iOS colour tokens and the four themes right, but nearly everything built on top of them differs: screen chrome, settings depth, sign-in, notifications, deep links and translations.

To keep it readable, paths are shortened with these prefixes:
- **I/** = `/Users/jonparis/mycode/astrid-ios/Astrid App/`
- **IR/** = `/Users/jonparis/mycode/astrid-ios/` (repo root)
- **A/** = `/Users/jonparis/mycode/astrid-android/app/src/main/java/com/gracefultools/astrid/`
- **AR/** = `/Users/jonparis/mycode/astrid-android/app/src/main/res/`

---

## 1. Design system

**What already matches:**
- Every palette token in `A/ui/theme/AstridColors.kt` equals iOS for Light, Dark and Ocean (`I/Utilities/Theme.swift:84-331`).
- Accent `#3b82f6` / pressed `#2563eb` / text `#ffffff` match `I/Utilities/Brand.swift:83-108`.
- Success, warning, error and info colours match. Priority colours `#6B7280` / accent / `#FBBF24` / `#EF4444` match.
- Spacing 2–48 and radii 6/8/12/16 match.
- The four theme modes and how each resolves (Ocean and Light force light, Auto follows the system) match `I/Models/ThemeMode.swift`.

**Inconsistencies:**

1. **Material colour roles left at default.**
   - iOS: n/a (all chrome is custom).
   - Android: `A/ui/theme/AstridTheme.kt:72-88` only maps primary, background, surface, surfaceVariant, outline and error.
   - Material 3 dialogs, date pickers, snackbars, switch tracks and chips read `surfaceContainer*`, `secondaryContainer`, `inverseSurface` and similar. These stay at Material's stock lavender/grey.
   - Change: map every role from the Astrid palette — `surfaceContainer*` to white or `bgSecondary`, `secondaryContainer` to `bgSelected`, `inverseSurface` to error/`textPrimary`.

2. **Missing tokens.**
   - iOS: `bgHover`, `buttonHover` (Ocean `#C8CCD1`), `borderFocus`, `borderInput`, `textSelected`, the shadow sizes (`shadowSmall` 0.05 opacity / radius 2 / y 1, `shadowMedium` 0.1/3/1, `shadowLarge` 0.1/15/10), and `caption2` at 11pt (`Theme.swift:62,70-71,82,145,170-189,239-242`).
   - Android: absent.
   - Change: add pressed, focus and selected-text tokens, shadow/elevation tokens and an 11sp style.

3. **Type scale not applied.**
   - iOS: views use `Theme.Typography.*` (headline 17 semibold, caption1 12, caption2 11).
   - Android: `AstridTypography` exists, but screens hard-code sizes. Examples: `A/ui/settings/SettingsScreen.kt:51,55,64` and `A/ui/tasks/TaskListScreen.kt:152`, where titles are 22sp but iOS headers are 17pt.
   - Change: route text through the typography styles, with screen titles at 17sp semibold.

4. **Screen header style.**
   - iOS: `FloatingTextHeader` in `I/Views/Components/FloatingHeader.swift:23-30, 97-122` is a floating card:
     - Padding 16×12, radius 12, shadow (black 0.08, radius 4, y 2), 8pt side margin.
     - Background white on Ocean, thin material on Light, `Dark.headerBg` on Dark.
     - Contents: `chevron.left` + an SF Symbol icon + a 17pt semibold title.
   - Android: flat rows with ArrowBack and a 22sp title — `SettingsScreen.kt:49-52`, `A/ui/account/AccountScreens.kt:59-62`, `TaskListScreen.kt:140-171`.
   - Change: build one shared `FloatingHeader` composable (white card on Ocean, radius 12, shadow, 8dp inset, optional icon) and use it on every screen.

5. **List header colour and status-bar inset.**
   - iOS: list header is the same white floating card, with the custom two-line hamburger icon (`I/Views/Components/HamburgerMenuIcon.swift:8-19`: two lines 19.2×2, 5pt apart).
   - Android: `TaskListScreen.kt:143` paints `colors.headerBg`, which is cyan on Ocean (it disappears into the background). It uses a hard-coded `padding(top = 36.dp)` instead of the status-bar inset, and Material's three-line Menu icon.
   - Change: use the floating card and the status-bar inset, and draw a two-line hamburger icon.

6. **Quick-add bar.**
   - iOS: `I/Views/Tasks/QuickAddTaskView.swift:132-185, 398-420`:
     - Priority checkbox image (`check_box_<p>`) on the left.
     - White input with a chrome border `#B4B8BC` on Light and Ocean; black input on Dark.
     - Placeholder dark grey on Ocean.
     - `plus.circle.fill` button.
     - Container is white at 0.8 opacity (glass on Light), radius 12, shadow y −2.
   - Android: `TaskListScreen.kt:204-238` uses `headerBg` (cyan on Ocean), a borderless input on `inputBg` (silver on Ocean), a Send arrow, and no priority control.
   - Change: match the container, input fill and border, add the leading priority checkbox, and use `AddCircle`.

7. **Checkbox artwork.**
   - iOS: 24 bitmap assets `check_box[_repeat][_checked]_<0-3>` (`I/Assets.xcassets/`), chosen at `I/Views/Tasks/TaskRowView.swift:502-515`.
   - Android: a vector square with a border (`A/ui/tasks/TaskRowView.kt:98-128`), and no repeat variant.
   - Change: export the iOS check-box assets as vectors, including the repeat variants.

8. **Row avatars always show "?".**
   - iOS: `TaskRowView.swift:394-418` shows the assignee photo (cached), with initials on an accent rounded square as fallback, a priority border and an overflow badge.
   - Android: `TaskRowView.kt:63` defaults `avatarInitial` to `{ "?" }`, and `TaskListScreen.kt:128` never passes it. There is no image loader dependency at all (`app/build.gradle.kts:63-76`).
   - Change: pass real initials, add an image loader (Coil) for photos, and add the overflow badge.

9. **Sidebar brand header.**
   - iOS: `I/Core/Layout/SidebarBrandHeader.swift:12-51` shows the AstridCharacter logo (28.8pt, radius 6), the lowercase wordmark `astrid` at 21.6pt semibold, and a search icon on the right.
   - Android: `A/ui/sidebar/SidebarDrawer.kt:54-57` shows "Astrid" as 28sp bold text, with no logo and no search.
   - Change: copy the iOS logo + wordmark + search row.

10. **Sidebar footer.**
    - iOS: `I/Views/Lists/ListSidebarView.swift:160-255` has a floating glass card: 40pt avatar (photo or initials) + name, opening Profile, and a `gearshape` button opening Settings.
    - Android: `SidebarDrawer.kt:90-98` has a plain "Settings" row.
    - Change: add the avatar and name card leading to Profile, plus a gear icon leading to Settings.

11. **Empty-state illustration.**
    - iOS: `EmptyStateView` / `AstridSpeechBubble` (`I/Views/Components/AstridSpeechBubble.swift:12-97`):
      - AstridCharacter at 160pt with a speech bubble: 16pt radius, 2pt border (cyan `#88DCF8` on Ocean), body semibold text, and a pointer arrow.
      - Message depends on context (`I/Views/Tasks/TaskListView.swift:1063-1125`), using keys `empty_state.my_tasks`, `.today`, `.public_list`, `.shared_list`, `.list_with_name`, `.default`.
    - Android: `TaskListScreen.kt:114-118` shows centred text "Nothing here yet."
    - Change: build the speech-bubble composable, ship the character art, and port the message logic and strings.

12. **Loading state.**
    - iOS: the same speech bubble with "Loading your tasks..." (`TaskListView.swift:787-797`).
    - Android: shows nothing while loading.
    - Change: show the bubble with `tasks.loading`.

13. **App icon.**
    - iOS: `I/Assets.xcassets/AppIcon.appiconset/` is the red octopus holding a green check on a light-blue background.
    - Android: `AR/drawable/ic_launcher_foreground.xml` is a placeholder white check in a circle on `#88DCF8`. The same drawable is used for the monochrome icon, the shortcut icon and the notification small icon.
    - Change: export the brand icon as adaptive foreground, background and monochrome layers, plus a separate notification glyph.

14. **Launch / splash screen.**
    - iOS: `I/Views/Components/SplashView.swift:21-99` draws an empty-list look-alike (header card, Astrid bubble "Loading your tasks...", empty quick-add bar).
    - Android: `AR/values/themes.xml:4-8` is an Ocean window background only; the auth check reuses the sign-in screen with a spinner (`A/ui/signin/SignInScreen.kt:59`). There is no `core-splashscreen`.
    - Change: adopt the SplashScreen API (brand icon on the theme background), then show a splash look-alike composable while the auth check runs.

15. **Dark-mode window flash.**
    - iOS: no equivalent issue.
    - Android: `themes.xml` uses `windowBackground` Ocean even when the theme is Dark, and there is no `values-night`.
    - Change: add `values-night`, or set the window background from the chosen theme.

16. **Status-bar icon contrast.**
    - iOS: `I/../AstridApp.swift:85,489-508` forces the interface style to match the theme (Ocean and Light are light, Dark is dark).
    - Android: `A/MainActivity.kt:27` calls `enableEdgeToEdge()` with defaults, which follow the system setting rather than the app theme. Light icons can end up on Ocean cyan, or dark icons on the Dark theme.
    - Change: pass a `SystemBarStyle` derived from `colors.isDark`.

17. **Error banner.**
    - iOS: `I/Core/Platform/AppErrorBanner.swift:13-30` is a red (`Theme.error`) rounded-10 bottom banner with a warning icon, white text, a close button, max width 520, shared by the whole app.
    - Android: each screen uses a default Material `SnackbarHost` (`TaskListScreen.kt:135`, `AccountScreens.kt:65`).
    - Change: give the snackbar the error colours and a warning icon, and host it once at the app root.

18. **Sheets vs dialogs.**
    - iOS: sheets with detents for the passkey dialog, share and edit profile (`I/Views/Authentication/LoginView.swift:165-183`, `I/Views/UserProfileView.swift:53-60`).
    - Android: everything is an `AlertDialog`, including the one named `NewTaskSheet` (`A/ui/tasks/NewTaskSheet.kt:19`).
    - Change: use `ModalBottomSheet` wherever iOS uses `.sheet`; keep `AlertDialog` only where iOS uses `.alert`.

19. **Leading icons on settings rows.**
    - iOS: every settings row has an accent SF Symbol: bell, cpu, link, paintbrush, globe, person.crop.circle, and `rectangle.portrait.and.arrow.right` on Sign Out (`I/Views/Settings/SettingsView.swift:49-336`).
    - Android: settings rows are plain text (`SettingsScreen.kt:62-66,100-104`).
    - Change: add the Material equivalents (Notifications, Memory, Link, Palette, Language, AccountCircle, Logout), tinted with the accent.

## 2. Settings and account

20. **Settings structure.**
    - iOS (`SettingsView.swift:43-341`), in order: Profile row; Sync (Last Sync relative + Sync Now); Features (Reminders); Exploratory Features (AI Agents, Connections, GitHub Issues, Google Tasks — the last two only when the server offers them); Preferences (Appearance, Language, Smart Task Creation with its description); Contacts; Sub tasks; Outbox; Account (Account & Access); About (Version, Build); Sign Out.
    - Android (`SettingsScreen.kt:54-104`): Account (name, email, waiting count); "Account and agents"; Reminders inline; Tasks; Appearance inline; About; Sign Out.
    - Change: reorder to match iOS and push Reminders and Appearance onto their own screens.

21. **Sync section.**
    - iOS: `SettingsView.swift:413-450` shows "Last Sync" (relative time or "Never") and "Sync Now" with a progress spinner.
    - Android: absent.
    - Change: add both rows, backed by the core's `sync` command and last-sync time.

22. **Outbox section.**
    - iOS: `SettingsView.swift:150-194` shows synced / in-flight / dropped counts, the reason for each dropped write, "Retry dropped writes" and Refresh.
    - Android: only "N waiting to send / Everything is sent" (`SettingsScreen.kt:57-60`).
    - Change: show dropped writes with their reasons and a retry button.

23. **Sign-out confirmation.**
    - iOS: `SettingsView.swift:379-388` asks "Are you sure you want to sign out?" with Cancel and a destructive button. The button itself is centred with an icon.
    - Android: `SettingsScreen.kt:100-104` signs out immediately; the text is left-aligned with no icon.
    - Change: add a confirmation `AlertDialog` and centre the button with an icon.

24. **About.**
    - iOS: separate "Version" and "Build" rows with values on the right (`SettingsView.swift:295-311`).
    - Android: one line "Version X · core Y" (`SettingsScreen.kt:97-99`).
    - Change: use two rows with trailing values (the core version can stay as a third row).

25. **Reminders settings incomplete.**
    - iOS: `I/Views/Settings/ReminderSettingsView.swift:26-117` has:
      - Push toggle that requests OS permission, plus "Open Settings to Enable Notifications" when permission is denied.
      - Email toggle.
      - Default reminder time picker.
      - Daily digest with time and timezone.
      - Quiet hours with start and end.
      - "Send Test Reminder" (shows an alert); pull to refresh.
    - Android: `SettingsScreen.kt:67-80` has three toggles and an inline radio list only.
    - Change: add digest time and timezone, quiet hours, test reminder, the permission request on toggle, and an Open-settings row (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).

26. **Settings labels differ from iOS.**
    - iOS: "Enable Push Notifications", "Send Email Reminders", "Send Daily Digest", "Default Reminder Time", "Smart Task Creation" plus the description "Parse dates, priorities, hashtags from titles".
    - Android: `AR/values/strings.xml` has "Push notifications", "Email reminders", "Daily digest", "Default reminder", "Smart task creation", and no description.
    - Change: use the iOS wording and add the description.

27. **Email-to-task.**
    - iOS: an information section in Appearance (`I/Views/Settings/AppearanceSettingsView.swift:88-144`): `remindme@astrid.cc` with an envelope icon and bullets for self / assigned / group / subject / body. There is no toggle.
    - Android: an "Email to task" on/off switch (`SettingsScreen.kt:83`) and no explanation.
    - Change: add the information block; drop the toggle or document it as a divergence.

28. **Appearance page.**
    - iOS (`AppearanceSettingsView.swift:47-159`): own page; theme as a segmented control; Task details list/project (behind the projectMode flag); "Show list images" toggle; email-to-task block; iPad landscape 3/2 columns and portrait 2-column/single.
    - Android: inline radio list (`SettingsScreen.kt:86-96`).
    - Change: own screen, theme as `SingleChoiceSegmentedButtonRow`, add the task-details, list-images and tablet layout options.

29. **Language picker.**
    - iOS: `I/Views/Settings/LanguageSettingsView.swift:28-80` lists Automatic plus 12 languages with a checkmark and a restart footnote. The Settings row shows the current language.
    - Android: absent. There is no `localeConfig` (`AR/xml/` has none).
    - Change: add a generated `locales_config.xml` plus an in-app picker via `AppCompatDelegate.setApplicationLocales` (Android 13+ system per-app language).

30. **Subtask display.**
    - iOS: `SettingsView.swift:132-148` offers "In lists, indented" or "Inside parent task only".
    - Android: absent.
    - Change: add the picker (synced user setting `subtaskDisplay`).

31. **Contacts.**
    - iOS: `SettingsView.swift:493-627`: upload contacts for collaborators, count, last sync, sync now, and an Open Settings prompt when denied.
    - Android: absent.
    - Change: add the same flow with `READ_CONTACTS`.

32. **Profile screen.**
    - iOS: `I/Views/UserProfileView.swift:160-378`:
      - User card: 80pt avatar (photo or initials on accent) with a camera badge for your own profile that opens Edit Profile; 24pt bold name; AI-agent badge; email; "Joined MMMM yyyy".
      - Stat cards: 48pt tinted icon tile (`checkmark.circle.fill` green, `lightbulb.fill` yellow, `heart.fill` blue), then "13 Completed" at 24 bold and the tagline at 15 medium muted. Card is `bgSecondary`, radius 8, 1pt border.
      - "Your tasks" shared-tasks section.
      - Loading and error states.
    - Android: `AccountScreens.kt:94-107` shows a big accent number (34sp) above the label, radius 12, 0.5 border, no user card, no icons, no tasks section.
    - Change: rebuild to the iOS layout, add Edit Profile, and move export off this screen (see 33).

33. **Account & Access screen.**
    - iOS: `I/Views/Settings/AccountSettingsView.swift:37-592`:
      - Photo picker plus editable name and email, with Save.
      - Email verification: send, resend, cancel change.
      - Account info: ID, created date, sign-in method.
      - Passkeys: list, add, rename, delete.
      - Your Data: "Export Data (JSON)" / "Export Data (CSV)" with a share sheet.
      - Danger Zone: Delete Account.
    - Android: absent. Export sits on Profile as bare "JSON"/"CSV" buttons (`AccountScreens.kt:108-118`).
    - Change: build the screen; passkeys can use Credential Manager or the web hand-off.

34. **Delete account.**
    - iOS: `I/Views/Settings/DeleteAccountView.swift:20-123`: red warning list, type-to-confirm phrase with live match/mismatch feedback, a final "Delete forever" alert, then sign out.
    - Android: absent.
    - Change: implement it — Play policy requires in-app account deletion.

35. **Agent Hub depth.**
    - iOS: `I/Views/Settings/AgentHubView.swift:23-428` plus `I/Core/Platform/AgentHubScreens.swift`:
      - Intro text, guide link and skill download.
      - Per agent: icon (bundled `ai-*` assets), `<mailbox>@astrid.cc`, "needs setup" badge.
      - Ownership segmented control (Astrid runs it / I run it / Off), then a transport picker.
      - API key editor (save, test, update, delete, "get a key" link); Copilot OAuth connect/disconnect.
      - Webhook settings screen; Copilot cloud-agent screen; Custom Agents (register, credentials, delete).
      - Connections link, "Astrid's model" picker (`DefaultAgentPickerView`), GitHub connection section.
      - Off rows dimmed to 0.65 opacity.
    - Android: `AccountScreens.kt:124-158` shows name and description plus mode chips only.
    - Change: port the ownership/transport model, key editor, webhook, custom agents, model picker and the error, loading and retry states.

36. **Agent mode labels.**
    - iOS: "%@ runs it" / "I run it" / "Off" for ownership; transports "Native coding harness" / "Webhook server" / "Custom Agent (SSE)".
    - Android: hard-coded "Astrid runs it", "Polling", "Webhook", "Off" (`AR/values/strings.xml`, `agents_mode_*`).
    - Change: use the iOS keys and substitute the brand name.

37. **Connections.**
    - iOS: `I/Core/Platform/ConnectionsScreen.swift:32-311`:
      - Subtitle "Everything that can act as your account" and empty state "Nothing is connected to your account yet."
      - Grouped sections (Yours / Third-party / Agent), "Acts as …".
      - Formatted Created / Last used / Expires dates.
      - Edit, and Revoke with a confirmation.
      - "New connection" OAuth client editor, plus a developer web link.
    - Android: `AccountScreens.kt:161-179` shows flat cards, the raw `lastUsedAt.take(10)`, and Revoke with no confirmation.
    - Change: add the sections, subtitle, empty state, formatted dates, revoke confirmation and new/edit client.

38. **GitHub Issues and Google Tasks sync.**
    - iOS: `I/Views/Settings/GitHubSyncSettingsView.swift`, `GoogleTasksSettingsView.swift`; rows in `SettingsView.swift:100-120`, shown only when the server offers them.
    - Android: absent.
    - Change: add both screens, gated on the same server capability.

39. **Sign-in layout.**
    - iOS: `LoginView.swift:29-82`:
      - White background (`Theme.bgPrimary`) even in Ocean, because it covers the app background.
      - A row with the 88pt AstridCharacter (radius 16) next to the wordmark (lowercase `astrid`, 34 bold) and the slogan (16).
      - Header text "Sign in to get started!" at 17 semibold.
      - Errors show the actual message with an error icon, left-aligned.
    - Android: `SignInScreen.kt:46-74` uses the Ocean background, "Astrid" at 44sp with no character art, and a generic error string.
    - Change: match the background, logo-plus-wordmark row, sizes and the real error message.

40. **Sign-in buttons.**
    - iOS: `LoginView.swift:193-245`:
      - Order comes from the server (`SignInMethods.offered`).
      - Google: accent background, white text, globe icon.
      - Passkey: neutral (white 0.95 / 0.2) with a key icon.
      - GitHub and SSO: neutral with `chevron.left.forwardslash.chevron.right` and `building.2` icons.
      - Height 50, radius 12, 17 semibold.
    - Android: `SignInScreen.kt:76-97` uses a fixed order, Google white, Passkey accent, GitHub `#24292F`, no icons, height 52, 15 medium.
    - Change: use the iOS colours and icons, order from server capabilities, height 50 and 17sp semibold.

41. **Use without an account.**
    - iOS: `LoginView.swift:90-135` adds an "or" divider, "Use without account" and the description. `WelcomeChoiceView` (`I/Views/Onboarding/WelcomeChoiceView.swift:29-105`) shows on first run. Settings shows "Create account" for local users (`SettingsView.swift:314-328`).
    - Android: absent.
    - Change: add local-only mode and the first-run welcome, if the core supports a local user.

42. **Passkey sign-in sheet.**
    - iOS: `LoginView.swift:352-452`: "New?" with an email field (sign up) or "Returning?" (sign in), as a 380pt sheet.
    - Android: hands everything to the browser page.
    - Change: acceptable divergence (hand-off). Document it, or add native Credential Manager passkeys.

43. **Help and feedback.**
    - iOS: a review prompt ("Loving Astrid?", after 5+ tasks, at most every 30 days). "Not really" leads to "Report an Issue" (opens the Bugs & Feedback public list) or "Send Feedback" (mailto `support@astrid.cc`, subject "Astrid App Feedback"). See `IR/AstridApp.swift:202-224`, `I/Core/Services/ReviewPromptManager.swift:9-121`.
    - Android: absent.
    - Change: add the prompt with the Play In-App Review API, the same feedback routes, and a "Send feedback" row in Settings.

44. **"A new version is ready" card.**
    - iOS: `I/Views/Components/AppUpdateReminderCard.swift:30-70` (`update.available_title` / `.action` / `.later`).
    - Android: absent.
    - Change: use Play In-App Updates, or the same `/api/v1/app-version` card.

## 3. Platform features

45. **Notification text.**
    - iOS: `I/Core/Notifications/NotificationManager.swift:66-67`: title "Task Due Soon", body = task title.
    - Android: `A/platform/Reminders.kt:92-93`: title = task title, body = "Reminder".
    - Change: swap so the title is "Task Due Soon" and the body is the task title, and localise.

46. **Notification actions.**
    - iOS: "Complete" (opens the app) and "Snooze 1 hour" (`NotificationManager.swift:361-376`). Foreground notifications show as banner, sound and badge (lines 333-337).
    - Android: Complete and "Snooze 1 hour" in the background, no action icons (`Reminders.kt:98-99`).
    - Change: add action icons. Completing without opening the app is acceptable on Android; document it.

47. **Full-screen reminder and triage on tap.**
    - iOS: tapping opens `ReminderView` (`I/Views/Reminders/ReminderView.swift`): a random Astrid phrase from `ReminderConstants` (`I/Core/Notifications/ReminderConstants.swift:29-118`), shared-list faces, "counting on you", Complete (green), and Snooze 15 min / 1 day / 1 week. With several due, it opens `ReminderTriageView` (assign to assistant, complete, tomorrow, keep) via `I/Core/Notifications/ReminderPresenter.swift:55-245`.
    - Android: the tap opens task detail (`Reminders.kt:80-84`).
    - Change: add a reminder screen and a triage deck opened from the notification intent, and port the nag phrases.

48. **Notification permission prompt.**
    - iOS: `I/Core/Notifications/NotificationPromptManager.swift:13-19,89-128` plus `IR/AstridApp.swift:184-201`:
      - Custom alert "Enable Push Notifications / Please enable push notifications so I can help remind you to get things done!"
      - Asked after sign-in or when a date is set; at most 3 times, then monthly.
      - If denied: "Notifications Disabled" with Open Settings.
    - Android: `A/MainActivity.kt:57-67` shows the system dialog straight away on the first signed-in screen, with no explanation and no recovery path.
    - Change: show the explanation dialog first, use the same triggers and limits, and offer Open-settings when denied.

49. **Notification icon, grouping and exact alarms.**
    - iOS: n/a.
    - Android:
      - Small icon is the launcher placeholder (`Reminders.kt:91`).
      - No group or summary.
      - The exact-alarm permission is never requested (`A/platform/ReminderAlarm.kt:63-67`).
    - Change: add a monochrome glyph, `setGroup` with a summary when there are several, and an "Alarms & reminders" settings row.

50. **Badge.**
    - iOS: `I/Core/Services/BadgeManager.swift:24-43` sets the app badge to the number of my incomplete tasks due today or overdue.
    - Android: absent.
    - Change: optional — set `setNumber` on the reminder notification for the launcher dot count.

51. **Deep links.**
    - iOS: `I/Core/Services/DeepLinkManager.swift:67-155` handles:
      - Custom scheme: `astrid://tasks/<id>`, `lists/<id>`, `users/<id>`, `settings/<page>`, `s/<code>`.
      - Web links: `/tasks/<id>`, `/t/<ident>`, `/lists/<id>`, `/settings/<page>`, `/s/<code>`.
      - Anything unknown opens in the in-app browser.
      - Settings pages: account, profile, reminders, agents, api-access, connections, chatgpt, contacts, appearance, debug, language, about (`I/Core/Services/SettingsPage.swift:8-21`).
    - Android: `A/DeepLinks.kt:23-28` and `AndroidManifest.xml:33-46` handle only `https://astrid.cc/t/…`, `astrid://task/<id>` (singular — iOS uses plural `tasks`) and `astrid://new`.
    - Change: accept `astrid://tasks|lists|users|settings|s` and web links for `/tasks|/lists|/settings|/s`, route settings pages to screens, and open unknown links in a Custom Tab.

52. **App links not verified.**
    - iOS: `applinks:astrid.cc`, `www.astrid.cc` (`I/Astrid App.entitlements:11-12`).
    - Android: `autoVerify` is off (`AndroidManifest.xml:33`).
    - Change: publish `assetlinks.json` and turn on `autoVerify`.

53. **Share extension.**
    - iOS: `IR/Astrid/Info.plist:27-43` accepts one image or one file only — text and URLs are disabled. `IR/Astrid/TaskQuickCreateView.swift:23-140` is a form: title (pre-filled from the file name), description, priority segmented control, attachment preview, "created in your default list". The file is uploaded as an attachment, and the task is assigned to you (`IR/AstridApp.swift:403-475`).
    - Android: `AndroidManifest.xml:27-32` accepts `text/plain` only. `NewTaskSheet.kt` is a one-line dialog that adds to the currently open list.
    - Change: accept `image/*` and `*/*` (including `SEND_MULTIPLE` capped at 1), add description, priority and attachment upload, and decide default list vs open list. Text sharing can stay as an Android extra.

54. **Home-screen shortcut.**
    - iOS: none (no `UIApplicationShortcutItems` anywhere).
    - Android: `AR/xml/shortcuts.xml` adds "New task".
    - Change: none — it is a reasonable extra — but `docs/PARITY.md:27` wrongly says iOS has one; fix the doc. Its icon is the placeholder.

55. **Widgets.**
    - iOS: none — no WidgetKit target.
    - Android: none.
    - Change: no parity gap. A Glance widget would be Android-only polish.

56. **Offline indicators.**
    - iOS: Chat shows "wifi.slash + Offline — messages will sync when connected" (`I/Views/Chat/ChatPanelView.swift:104-116`); comments show "wifi.slash + Offline" (`I/Views/Tasks/CommentSectionViewEnhanced.swift:365-372`); offline photo-pick messages appear in detail.
    - Android: only per-row "Waiting to send"; no connectivity state reaches the UI (`A/platform/NetworkWatch.kt` only tells the core).
    - Change: expose network state and show the same strips in chat and comments.

57. **Background refresh.**
    - iOS: BGProcessingTask `cc.astrid.app.sync`, scheduled on background (`IR/Info.plist:83-90`).
    - Android: WorkManager every 15 minutes (`A/platform/BackgroundSync.kt`).
    - Change: equivalent, no change needed. Reminders also go through AlarmManager.

58. **Localisation coverage.**
    - iOS: 1,343 keys in each of 12 locales.
    - Android: `AR/values/strings.xml` has 145 strings plus 71 in `core_keys.xml`.
      - Every locale (`values-de`, `-es`, `-fr`, `-it`, `-ja`, `-ko`, `-nl`, `-pt`, `-ru`, `-zh-Hans`, `-zh-Hant`) lacks the same 42 app strings (one is `app_name`): `agents_mode_api/polling/webhook`, `auth_open_browser_again`, `auth_waiting_for_browser`, `board_move_to/show_board/show_list`, `chat_placeholder`, `common_dismiss`, `detail_add_comment/add_description/copy_link/more/pick_date/pick_time/select_a_task`, `lists_invite_email/leave/settings`, `navigation_open_sidebar`, `priority_a11y`, `profile_export`, `repeat_custom_title/ends_after/ends_on_date/times`, `settings_all_sent/daily_digest/email_to_task/more/push_reminders/smart_tasks/version/waiting_to_send`, `share_add_to`, `tasks_complete_a11y/empty/reopen_a11y/unassigned_glyph/waiting_to_send`.
      - All locales except `de` also lack 42 of the 71 core-label keys.
    - Change: translate from the matching iOS keys. The missing `*_a11y` strings mean TalkBack speaks English in every locale.

59. **iOS strings with no Android equivalent.**
    - iOS: whole key groups for features Android lacks: `empty_state.*`, `welcome.*`, `splash.loading`, `tasks.loading`, `settings.reminders.*` (quiet hours, digest, test), `settings.account.*` (≈60 keys: passkeys, verification, delete), `settings.agents.*` / `settings.openclaw.*` / `settings.connections.*` (≈150 keys), `settings.appearance.*`, `language_*`, `contacts.*`, `sync.*`, `update.*`, `reminders.*` (snooze, triage), `profile.*` (joined, your_tasks, errors), `chat.offline`.
    - Android: absent.
    - Change: port each key with its screen.

60. **Accessibility.**
    - iOS: system controls (Toggle, Picker) carry their labels; Dynamic Type through `Theme.Typography`; explicit labels and identifiers (for example `SidebarBrandHeader.swift:50-51`, `I/Views/Reminders/ReminderTriageView.swift:83,121`).
    - Android:
      - `SettingsScreen.kt:110-115`: the toggle row is a Text plus an unlabelled Switch; it needs `Modifier.toggleable(role = Switch)` on the row.
      - `SettingsScreen.kt:75-78,91-94`: radio rows have two focus targets; they need `selectable(role = RadioButton)` with `onClick = null` on the button.
      - Group titles (`SettingsScreen.kt:120`, `SidebarDrawer.kt:105`) lack `heading()`.
      - Navigation rows (`SettingsScreen.kt:64`) lack `role = Button` and a chevron.
      - Agent mode chips (`AccountScreens.kt:137-142`) lack `Role.RadioButton`.
    - Change: fix the semantics as listed.

---

`/Users/jonparis/mycode/astrid-android/docs/PARITY.md` is out of date in four places:
- **Line 22:** marks Settings ✅, though most of iOS's settings are missing.
- **Line 27:** says iOS has a Home Screen quick action; it doesn't.
- **Line 30:** says the two-pane tablet layout is planned; `A/ui/AppRoot.kt:92-93` already ships two panes at ≥600dp.
- **Line 31:** says the Agent Hub, Connections and Profile screens aren't built; partial versions exist.
