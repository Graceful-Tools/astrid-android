# iOS parity audit (2026-10-19): Task rows, quick add, detail, comments, reminders

*Read-only comparison of astrid-ios and astrid-android source. Each finding names the iOS evidence, the Android evidence, and what Android must change. Work is tracked as AATD tasks that cite these item numbers.*

# Android vs iOS parity audit: tasks

I compared the task screens in both apps by reading the code. Nothing was run or edited. Android implements far less of this area than iOS: about 1,000 lines in total for the task row, list, detail, view models, markdown and reminders, against more than 8,000 on iOS. Most of the gaps below are features that are simply missing.

**Path roots used below**
- iOS = `/Users/jonparis/mycode/astrid-ios/Astrid App/`
- AND = `/Users/jonparis/mycode/astrid-android/app/src/main/java/com/gracefultools/astrid/`
- iOS English strings: `/Users/jonparis/mycode/astrid-ios/Astrid App/Resources/Localizations/en.lproj/Localizable.strings`
- Android strings: `/Users/jonparis/mycode/astrid-android/app/src/main/res/values/strings.xml`

**Four things look like outright bugs rather than gaps:**
1. **Avatars always show "?".** The row's `avatarInitial` defaults to `{ "?" }` (AND `ui/tasks/TaskRowView.kt:63`). The list, board and search screens never pass it (`TaskListScreen.kt:128`, `BoardScreen.kt:192`, `SearchScreen.kt:67`). The core already sends `leading.initial` (`model/Rows.kt:45`), but nothing reads it.
2. **Time is dropped for Today/Tomorrow/Yesterday.** `dueText` returns only the day word for those keys (AND `ui/tasks/TaskRowView.kt:166-169`). A task due "today at 6 PM" reads just "Today", in both the row and the detail.
3. **Read-only ("copy only") tasks can still be edited.** In detail, the When, Priority, Lists and Reminder fields ignore `isCopyOnly` (AND `ui/detail/TaskDetailScreen.kt:246, 322, 349, 524`). Only the title, assignee and repeat respect it.
4. **Tapping a #list or !task reference in a description does nothing.** `MarkdownView` defaults `onReference` to a no-op and detail never overrides it (AND `ui/markdown/MarkdownView.kt:44`, `TaskDetailScreen.kt:402`). iOS opens the reference.

---

## 1. Task row

| # | Issue | iOS evidence | Android evidence | What Android must change |
|---|---|---|---|---|
| R1 | Checkbox artwork | iOS `Views/Tasks/TaskRowView.swift:497-516` uses image assets `check_box[_repeat][_checked]_<0-3>` (in `Assets.xcassets/`). Unchecked is an outlined box in the priority colour. Checked is that box with a large **green tick** overflowing it. Repeating tasks use a box drawn as **circular arrows**. | `TaskRowView.kt:107,117` draws a 2dp border, and when checked a priority-coloured fill with a white check. There is no repeat variant. | Port the 16 PNGs (or equivalent vectors) and pick the same name. |
| R2 | Avatar face | `TaskRowView.swift:390-425`: the photo loads (`CachedAsyncImage`), falls back to initials on an accent square, and has a "+N" overflow badge for multiple assignees (`Core/Layout/AssigneeOverflowBadge.swift`). | `TaskRowView.kt:108,118`: accent square showing "?" (bug 1 above). No photo, no badge. | Draw `assignee.image`, fall back to `leading.initial`, and add the +N badge. |
| R3 | Metadata icons iOS doesn't show | `TaskRowView.swift:182-262`: the metadata line has only the board identifier, the due text, and list chips. There is no clock, repeat, lock or comment icon. | `TaskRowView.kt:142-151` adds a clock icon, repeat icon, lock icon and comment bubble with count. | Remove them to match iOS, or log the divergence in `docs/PARITY.md`. Attachment and subtask counts are absent on **both**, which is consistent. |
| R4 | Overdue styling | `TaskRowView.swift:197-204`: due text is always muted (`textMuted`). No overdue red. | `TaskRowView.kt:143-144` turns overdue text and icon red. | Use muted text, or document why Android differs. |
| R5 | Due-date format | Timed tasks always show "MMM d h:mm a", e.g. "Jan 5 6:26 PM" (`TaskRowView.swift:202,521-527`). All-day tasks show Today/Tomorrow/Yesterday, else a medium date (`Core/Layout/DueDateLabel.swift:78-95`). | `TaskRowView.kt:166-179`: Today/Tomorrow/Yesterday with no time (bug 2); other dates as `FormatStyle.MEDIUM` ("Jan 5, 2026") plus the time. | Timed: "MMM d" plus short time. All-day: the day word or a medium date. |
| R6 | List chip style | `TaskRowView.swift:213-256`: 15pt text in `textSecondary`. Leading glyph: a tag symbol, a green globe (public), `person.2` (shared) or `number` in the list colour. Background is grey (`Ocean.bgTertiary`, or `bgSecondary` in dark) with a **1pt border**, radius 12, padding 10/4. Shows "+N" past 2 chips. | `TaskRowView.kt:152-159`: 13sp `textPrimary`, background is the list colour at 18% alpha, no border, no glyph, no "+N". | Match the font, colours, border, glyph and "+N" overflow. |
| R7 | Title chips | `TaskRowView.swift:159-169`: "Won't do" chip (`tasks.wont_do_chip`), sync chip ("Syncing" / "Sync failed"), and GitHub `repo#N` remote link chip. Outlined, radius 4, caption size. | Absent. | Add them (needs `closedReason`, sync state and remote fields from the core). |
| R8 | Title line limit | No limit on iPhone; one line in compact mode when the iPad detail pane is open (`TaskRowView.swift:149`, `Views/Tasks/TaskListView.swift:849`). | `maxLines = 3` always (`TaskRowView.kt:89`). | No limit on phone; one line in two-pane mode. |
| R9 | Card background | Ocean: white at **80% opacity** (100% when selected). Dark: `Theme.Dark.bgPrimary` (17,24,39). (`TaskRowView.swift:453-459`.) | Solid white; dark uses `bgSecondary` (31,41,55) (`TaskRowView.kt:73`). | Match: white 0.8 on Ocean, `bgPrimary` in dark. |
| R10 | Selected row in two-pane | `TaskRowView.swift:284-301`: 2.5pt accent border and a triangle arrow pointing at the detail pane. Tapping the same row closes the pane (`TaskListView.swift:871-872`). | No selected state. Tapping again doesn't close (`ui/AppRoot.kt:95`). | Pass `isSelected` into the row; make a second tap toggle the pane. |
| R11 | Long-press menu | `TaskRowView.swift:314-322`: long-press shows "Copy task id" (`tasks.taskId.copy`) when the task has an id. | Absent; the row is `clickable` only (`TaskRowView.kt:75`). | Add a long-press dropdown with "Copy task id". |
| R12 | Subtask indent | 16pt per level, capped at 4 levels (`TaskListView.swift:805-807`). | `depth * 24` dp, no cap (`TaskRowView.kt:71`). | Use 16dp and cap at 4. |
| R13 | Row spacing | Insets: 8 leading/trailing, 4 top/bottom, first row 8 top (`TaskListView.swift:860-865`). | 12dp horizontal padding, 8dp gaps (`TaskListScreen.kt:122-123`). | Use 8dp sides and 8dp between rows. |
| R14 | Leading-control picker (project mode) | When the shared rule says `openPicker`, the row opens a 280pt popover: priority buttons, assignee picker, "Project state" chips, and a Complete / "Mark Incomplete" button (`TaskRowView.swift:132-139`; `Views/Components/TaskQuickChanger.swift`). | Only a column-name dialog titled "Move to" (`ui/common/StatusPicker.kt`). | Build the full quick-changer content (priority, assignee, state, complete). |
| R15 | Haptics | Success haptic on complete, light haptic on reopen (`TaskRowView.swift:69-76`). | None. | Use `HapticFeedback` (Android's own conventions). |
| R16 | Pending state | iOS shows no "pending" text on rows (it shows the sync chip only for GitHub boards). | 0.7 alpha plus "Waiting to send" (`TaskRowView.kt:78,160`). | Fine as an Android offline cue, but record it in PARITY.md. |
| R17 | Leading control in search results | Search results use the same row as the list, so the checkbox completes (`TaskListView.swift:829-842`). | Search rows open the task when the leading control is tapped (`ui/search/SearchScreen.kt:67`). | Wire it to complete or leading as in the list. |

## 2. List: swipe, reorder, completion, undo

| # | Issue | iOS | Android | What Android must change |
|---|---|---|---|---|
| L1 | Swipe actions | Only the standard trailing "Delete" swipe (`.onDelete`, `TaskListView.swift:887`). **No swipe-to-complete.** | Swipe right completes (green), swipe left deletes (`TaskListScreen.kt:176-202`). | Swipe-right-to-complete has no iOS counterpart. Keep it as a documented Android convention or remove it; PARITY.md wrongly says it matches. |
| L2 | Delete wording on GitHub boards | Uses "Remove from board" via `TaskCapabilities.deleteWording` (`Core/Tasks/TaskCapabilities.swift:54-73`). | Always "Delete". | Use the same wording rule. |
| L3 | Manual reorder and drag-to-nest | `.onMove` plus `TaskRowNestingDrop`: drag onto a row to make a subtask, drag into the gutter to outdent; drop-zone strings `subtasks.promote_drop_target` / `outdent_drop_target` (`TaskListView.swift:879-888, 1409, 1431`). | Absent from the list (the board has its own drag). | Add drag reordering and nesting. |
| L4 | Completion animation and undo | No undo or toast anywhere (`actions.undo` exists but is unused; `completedToast` in `Core/Notifications/ReminderConstants.swift:96` is unused). The row just flips to struck-through. | No undo, optimistic flip (`ui/tasks/TaskListViewModel.kt:104-114`). | Consistent; no change. |
| L5 | Errors | Errors go to `AppErrorCenter` (`Core/Platform/AppActions.swift:25`). | Snackbar (`TaskListScreen.kt:100-102`). | Acceptable as an Android convention. |
| L6 | Empty state | Context-specific messages `empty_state.*`, e.g. "Let's fill up \"%@\"!…" (`TaskListView.swift:1072-1112`; strings file lines 286-292). | "Nothing here yet." (`TaskListScreen.kt:114-118`). | Port the per-list empty-state messages. |

## 3. Quick add

| # | Issue | iOS (`Views/Tasks/QuickAddTaskView.swift`) | Android (`ui/tasks/TaskListScreen.kt:204-238`) | What Android must change |
|---|---|---|---|---|
| Q1 | Leading checkbox / picker | A 34pt button at line 106-118 showing `check_box_<priority>` or the chosen assignee's avatar. Tapping closes the keyboard and opens `QuickAddPickerSheet` (priority + assignee, half-height sheet) at 206-216. | Absent. | Add the checkbox and a bottom sheet for priority and assignee, and send both with `createTask`. |
| Q2 | Add button | `plus.circle.fill` at title2 size, muted when empty, accent otherwise. Tapping it **creates the task and opens its detail** (182-190, 638-644). | Send icon; creates without navigating (234-237). | Use a plus-circle and navigate to the new task. Return key keeps adding in place. |
| Q3 | Input behaviour | Multi-line editor that grows up to the space above the keyboard (85-93, 141-173). Return submits and keeps focus. Return on an empty field dismisses the keyboard (157-170). Dragging down dismisses (199-205). | `singleLine`, `ImeAction.Done`. Empty Done does nothing. | Multi-line growing field, empty-submit dismisses, scroll-down dismisses. |
| Q4 | Optimistic clear | Clears the text immediately; restores it on failure (460-463, 649-655). Guards against double submit (451). Success haptic (467). | Clears only after success (211). No double-submit guard. | Clear immediately, restore on error, guard, add haptic. |
| Q5 | Bar chrome | Floating card: radius 12, shadow (0.1, r8, y-2), 8pt side margin, background white 0.8 (Ocean/Light) or `Dark.headerBg`, padding 16/12 (12-25, 195-197, 439-447). Field is **white** with a **1pt `Ocean.inputBorder`** border, radius 8 (174-179, 405-420). | Full-width `headerBg` bar. Field is `inputBg`, which is **silver (220,223,228) on Ocean**, radius 12, no border (214-232). | Match the floating card and the white bordered field. |
| Q6 | List defaults | Applies the list's default priority, assignee, due date/time, privacy and repeat on the client (238-276, 521-582). | Leaves this to the core; forwards only the virtual-list assignee (`ui/tasks/TaskListViewModel.kt:121-149`). | Verify the core applies all of these defaults; otherwise forward them. |
| Q7 | Board column add | Each board column has its own flush quick-add footer that sets status and extra list ids (48-56). | Absent. | Possibly the board audit's area; flagging it. |

## 4. Task detail: structure, header and menu

| # | Issue | iOS | Android | What Android must change |
|---|---|---|---|---|
| D1 | Header bar | `Views/Tasks/TaskDetailHeaderBar.swift`: back chevron; a centred "Task Details" title (`tasks.task_details`) that scrolls to the top when tapped; a full-screen toggle in the iPad pane; and a vertical ⋮ menu. | Back arrow, the identifier text, and ⋮ (`ui/detail/TaskDetailScreen.kt:160-180`). | Add the centred title with tap-to-top, and an expand/collapse control in two-pane. |
| D2 | ⋮ menu items | `TaskDetailHeaderBar.swift:131-166`: **Copy** (`doc.on.doc`), **Share** (`square.and.arrow.up`), divider, **Won't do / Reopen**, **Delete task** (destructive) or "Remove from board". | "Copy link", then Delete (`TaskDetailScreen.kt:170-177`). | Add Copy (sheet), Share (sheet), Won't do / Reopen (`setClosedReason`), and use "Delete task" wording. "Copy link" belongs inside the Share sheet. |
| D3 | Delete confirmation | Confirmation dialog: `tasks.delete_confirm` "Delete this task?", buttons Delete / Cancel (`Views/Tasks/TaskDetailViewNew.swift:172-184`). | Deletes immediately (`TaskDetailScreen.kt:175`). | Add an AlertDialog. |
| D4 | Copy task | `CopyTaskView` sheet (`Views/Tasks/TaskActionsView.swift:172-221`): "Target List" picker defaulting to "My Tasks (only)", an "Include Comments" toggle, and a "Copy Task" button. | Absent (only a copy-only row's leading control calls `copyTask`). | Add a copy sheet with list choice and include-comments option. |
| D5 | Share task | `Views/Components/ShareTargetView.swift`: generates a link, shows a privacy message (`share.private_task_message`), has "Copy to Clipboard" → "Link copied!", a Share button, and an error string. | "Copy link" copies `detail.link` silently. | Add a share sheet using `Intent.ACTION_SEND`, with copied feedback. |
| D6 | Overall layout | No cards. Divider under the title and after the fields. Rows are `TwoColumnRow` with a **34pt icon column** (centred under the checkbox) instead of a text label (`Views/Components/TwoColumnRow.swift`). Icons: Who `person.crop.circle`, When `calendar`, Priority text glyph "!!!", Lists `list.bullet`, Task ID, Project state `square.grid.2x2`, Waiting on `hourglass`, Occurrences `repeat`. | Fields sit inside a white rounded Card, each with a **96dp text label** (`TaskDetailScreen.kt:110-126, 212-236`). | Replace text labels with the 34dp icon column; drop the Card and use dividers. |
| D7 | Background | Ocean: cyan with a white 0.8 overlay, i.e. near-white (`TaskDetailViewNew.swift:629-660`). | `bgPrimary`, which is cyan on Ocean, with white cards on top (`TaskDetailScreen.kt:100`). | Use the white-0.8 overlay. |
| D8 | Title field | 19pt medium, **multi-line**, `bgSecondary` rounded box with 12 padding (`TaskDetailViewNew.swift:1299-1316`). | 22sp SemiBold, single line, transparent, accent underline when focused (`TaskDetailScreen.kt:197-207`). | Match the font and the box; allow wrapping (strip newlines as iOS does). |
| D9 | Leading control in detail | `Views/Tasks/TaskDetailLeadingControl.swift`: the same face as the row (repeat-aware checkbox image, avatar, or "U"). In project mode it opens a 320pt popover with priority, assignee, project state, and a Complete / "Mark Incomplete" button. | A plain bordered square that only completes (`TaskDetailScreen.kt:188-195`). | Reuse the row's leading control and add the quick-changer popover. |
| D10 | "Complete sub-tasks?" | Completing a parent with open subtasks asks: "Mark all sub-tasks complete" / "Only this task" / Cancel (`TaskDetailViewNew.swift:264-276, 1428-1440`). | Absent. | Add the dialog; complete each subtask through `completeTask`. |
| D11 | Pull to refresh | `.refreshable` drains the outbox and refetches (`TaskDetailViewNew.swift:595-597, 1320`). | Absent. | Add `PullToRefreshBox`. |
| D12 | Display mode | List mode shows Who and Priority rows; project mode hides them behind the leading control (`TaskDetailViewNew.swift:322, 413`; `Models/TaskDisplayMode.swift`). | Always renders the core's `fieldOrder`; there is no display-mode setting in the app. | Add the display-mode setting and honour it. |
| D13 | Read-only / public task | Hides editors, shows a static title box, and a "Created By" row with avatar linking to the profile (`TaskDetailViewNew.swift:280-288, 339-366`). | See bug 3; no "Created By" row. | Make every field read-only for copy-only tasks and add "Created By". |
| D14 | Notification permission prompt | Asks after a due date is set: "Enable Push Notifications" / "Please enable push notifications so I can help remind you…" / Not Now / Enable, with an Open Settings fallback (`TaskDetailViewNew.swift:206-224, 1580`). | Asks once at app launch (`MainActivity.kt:61-65`). | Ask in context, after the first date is set, with iOS's wording. |

## 5. Task detail: fields

| # | Field | iOS | Android | What Android must change |
|---|---|---|---|---|
| F1 | Who: display | Avatar (24pt) plus name, or "U" plus "Unassigned", plus extra faces, plus a chevron (`Views/Components/InlineAssigneePicker.swift:398-490`). | Name text only (`TaskDetailScreen.kt:471-473`). | Show the avatar and name, and the "U" mark when unassigned. |
| F2 | Who: picker rows | 32pt avatar or initials, name, a "You" or **"AI Agent"** subtitle, a check; "Loading AI agents..." while fetching (`InlineAssigneePicker.swift:355-365, 501-597`). | Text only. Shows the raw userId when a name is missing (`TaskDetailScreen.kt:477`). `isAgent`, `isCurrentUser`, `image` and `initials` are decoded but unused (`model/Detail.kt:85-92`). | Render avatars and the You / AI Agent subtitles. |
| F3 | Who: someone else | "Someone else" section: "Invite by Email" field, contact search, Assign button, and the error "Add this task to a list first…" (`InlineAssigneePicker.swift:238-330`). | Absent. | Add assign-by-email. |
| F4 | Who: multiple assignees | Toggles up to 10 people (an agent goes first) via `setAssignees` (`InlineAssigneePicker.swift:504-514`; `Core/Layout/AssigneeSelection.swift`). | Single assignee only. | Support multi-assign where the list allows it. |
| F5 | When: row shape | One row with **separate** Date, Time and Repeat chips that wrap. Time and Repeat only appear once there is a date (`TaskDetailViewNew.swift:367-405`; `Core/Layout/TaskWhenRowLayout.swift`). | One "When" row opening one dialog. Repeat is its own row, always shown (`TaskDetailScreen.kt:121`). | Split into three chips; hide time and repeat until a date exists. |
| F6 | Date chip and sheet | Label: Today/Tomorrow/Yesterday, else "EEE MMM d yyyy" (with weekday). Sheet: red "No due date" row with a check, quick-pick rows with checkmarks, an inline graphical calendar that saves on selection, and Close (`Views/Components/InlineDatePicker.swift`; `DueDateLabel.swift:22-34, 175-178`). | AlertDialog with chips plus "Pick a time" / "Pick a date" / "No due date" buttons; Material date dialog needs "Done" (`TaskDetailScreen.kt:249-301`). Medium date with no weekday. | Use a bottom sheet with list rows and an inline calendar; add the weekday. |
| F7 | Time chip and sheet | Chip shows the time, or a slashed clock icon. Sheet: red **"All day"** row (removes the time, keeps the date), quick times **"Morning (9 AM)" / "Afternoon (2 PM)" / "Evening (6 PM)" / "Night (9 PM)"**, a wheel picker, and a "Set" button (`Views/Components/InlineTimePicker.swift`). | Times are chips labelled with a bare formatted hour ("9:00 AM") (`TaskDetailScreen.kt:265-269`). No way to go back to all-day. | Use the `picker.*` labels and add an "All day" option. |
| F8 | Repeat chip and list | Chip: accent repeat icon plus a summary (custom patterns use `CustomRepeatSummary`), or a slashed repeat icon. List: every option with a check, the selected row tinted accent 15% with an accent border, and Cancel (`Views/Components/InlineRepeatPicker.swift`). | Plain picker dialog (`TaskDetailScreen.kt:486-515`). | Match the chip and row styling. |
| F9 | Custom repeat editor | `Views/Components/CustomRepeatingPatternEditor.swift`: interval wheel 1-99, unit menu, weekday **circles** "M T W T F S S" (at least one must stay selected); monthly "Same date every month" / "Same weekday and week of month" with day-of-month, weekday and 1st-5th; yearly month and day; **Repeat Mode** (`repeating.from_completion` / `from_due_date`); end Never / After X (wheel) / Until date; and a Preview. | `ui/detail/CustomRepeatDialog.kt`: interval, unit, weekday chips, ends. **Missing:** monthly options, yearly month/day, repeat mode, preview. Weekday selection can be emptied. | Add monthly/yearly options, `repeatFrom`, preview, and keep at least one weekday. |
| F10 | Repeat from due date | `editedRepeatFrom`; the summary shows "(from due date)" (`InlineRepeatPicker.swift` summary function). | No `repeatFrom` anywhere. | Add it, and carry it into rollover. |
| F11 | Reminder row | **iOS detail has no reminder field.** Reminders use the account-wide default offset (`Core/Notifications/NotificationManager.swift:110-118`). | Has a "Reminder" row (`TaskDetailScreen.kt:122-125, 517-534`). | Remove it, or record it in PARITY.md as an Android addition. |
| F12 | Priority control | Glyphs **"○"** / ! / !! / !!!, buttons 40×40, radius 6, 12pt spacing, light haptic, row marked "!!!" (`Views/Components/PriorityButtonPicker.swift:96-147`). | "–" glyph, 40×32, radius 8, 6dp spacing (`TaskDetailScreen.kt:320-342`). | Use "○", 40×40, radius 6dp, 12dp spacing. |
| F13 | Lists: display | Empty shows "Add to lists" plus a chevron; otherwise `ListBadge` (list icon, caption, `bgTertiary`, radius 6) in a `bgSecondary` box (`Views/Components/InlineListsPicker.swift:120-145, 241-257`). | "—" when empty; colour-tinted chips (`TaskDetailScreen.kt:349-357`). | Match. |
| F14 | Lists: picker | "Search lists..." field, list icons, `checkmark.circle.fill` / `circle`, "No lists found", Cancel and Close with auto-save (`InlineListsPicker.swift:44-110, 205-235`). | Material checkbox dialog with Save/Cancel; no search, even though the view model accepts a query (`TaskDetailViewModel.kt:110`). `ListPicks.createName` is unused. | Add search, list icons and round checks, and create-list if the core offers it. |
| F15 | Task ID | A "Task ID" row in the icon column; long-press copies ("Copy task id") (`Views/Tasks/TaskDetailIdentifierRow.swift`). | Identifier shown in the top bar, not copyable (`TaskDetailScreen.kt:167`). | Move it to a row and add a long-press copy menu. |
| F16 | Project state | "Project state" row with state chips in list mode (`TaskDetailViewNew.swift:462-471`; `Views/Components/ProjectStateQuickPicker.swift`). | Absent from detail. | Add it. |
| F17 | Waiting on (blockers) | "Waiting on" (hourglass): chips with strikethrough when done and a lock when hidden; × "Stop waiting on this task"; + opens a searchable sheet ("Search tasks…", "No matching tasks", errors `cycleError` / `addError`) (`Views/Tasks/TaskDetailBlockersRow.swift`). | Absent. | Add it. |
| F18 | Occurrences | "Occurrences" row with "Previous" / "Next" chips (`Views/Tasks/TaskDetailOccurrencesRow.swift`). | Absent. | Add it. |
| F19 | GitHub project fields | `TaskDetailProjectFieldsRows` with their error strings (`Views/Tasks/TaskDetailProjectFieldsRows.swift`). | Absent. | Add it. |
| F20 | Description | Caption label "Description". Display: `bgSecondary` box, padding 12, radius 8, references coloured and tappable. Edit: `bgTertiary`, minimum 100pt high, keyboard "Done". Placeholder "Add a description..." (`Views/Components/InlineTextEditor.swift:33-130`; `TaskDetailViewNew.swift:487-495`). | 15sp SemiBold section header, Card, OutlinedTextField with an inline Done, "Add a description", references not tappable (bug 4) (`TaskDetailScreen.kt:385-407`). | Match the label, boxes and placeholder; wire reference taps to open the task or list. |
| F21 | Attachments section | "Attachments (n)" plus a horizontal strip of 64pt thumbnails; tapping opens a gallery preview across all task files (`Views/Tasks/TaskAttachmentSectionView.swift`). | Absent; files only appear inside comments. | Add the strip and a preview pager. |
| F22 | Parent task link | "Parent task:" plus the title with a back-turn arrow; opens the parent (`TaskDetailViewNew.swift:524-543`). | Absent. | Add it. |
| F23 | Subtasks | Header "Sub tasks" plus "done/total" plus a recursive total. Each row: round check (20pt, accent when done) that toggles, strikethrough title, tap opens the subtask. Always ends with a "+ Add a sub task" field that keeps focus (`Views/Tasks/SubtasksSectionView.swift`). | Hidden when there are none. No toggle, no tap-through, no count, no add field. Header reads "Subtasks" (`TaskDetailScreen.kt:128-140`). | Implement it fully. |
| F24 | Timer | Timer button in the comment send slot while the box is empty; full-screen `TaskTimerView`; "Last: %@" caption (`TaskDetailViewNew.swift:194-204, 501-509`; `Views/Tasks/TaskTimerView.swift`). | Absent. | Add it. |
| F25 | Completed via | "Completed via Google Tasks / GitHub / Apple Reminders" (`TaskDetailViewNew.swift:512-521`). | Absent. | Add it. |

## 6. Comments, chat with agents, activity

| # | Issue | iOS (`Views/Tasks/CommentSectionViewEnhanced.swift` unless noted) | Android (`ui/detail/TaskDetailScreen.kt:409-463`) | What Android must change |
|---|---|---|---|---|
| C1 | Header | "Comments (%d)" (headline), "Show system / Hide system" toggle, offline indicator, loading spinner (347-380). | Fixed "Comments" label. | Add the count and the system toggle; hide system comments by default. |
| C2 | Empty state | "No comments yet. Be the first to comment!" (383-387). | Nothing. | Add it. |
| C3 | Bubble layout | Chat style: other people on the left with a 32pt avatar, you on the right with your avatar. Below the bubble: "You" or the author's name · time. Mine is white (Ocean) or `bgSecondary`; others are white 50%. Radius 8, padding 12 (1291-1380, 1228-1248). | Full-width blocks, name above, mine `bgSelected` (light blue), no avatar, no timestamp. | Rebuild as aligned bubbles with avatars and "name · time" (needs author id/image from the core). |
| C4 | Comment markdown | `MarkdownText` block rendering with coloured @mentions, #lists and !tasks (1316-1320). | Plain `Text(comment.content)`; `CommentRow` has no blocks. | Ask the core for comment blocks and render them with MarkdownView. |
| C5 | System comments | Centred caption: "On <date>, <content>" (1284-1289). | Left-aligned muted text inside a bubble. | Match. |
| C6 | Completion streak | Repeated completion lines fold into a flame row, "Completed %1$d times in %2$d days", which expands to the dates (391-394; `Views/Tasks/CompletionStreakRow.swift`). | Absent. | Add it. |
| C7 | Long-press menu | Copy / Reply, then (for your own comments) Edit / Delete (1251-1280). Delete confirms with "Delete Comment" / "Are you sure you want to delete this comment?" (1401-1415). Edit opens a half-height sheet with Cancel/Save (1416-1447). | Absent. | Add a long-press menu, edit dialog and delete confirmation. |
| C8 | Replies | Reply sets a "Replying to X" banner with Cancel and posts with `parentCommentId` (443-455, 776-826). Replies nest (1383-1399). | Shows indented replies (`isReply`) but cannot create one. | Add reply. |
| C9 | Reactions | Not present in iOS. | Not present. | Consistent. |
| C10 | Agent typing | `AgentTypingIndicator`: three bouncing dots and "<agent> is thinking..." from the live stream (421-424; `Views/Chat/AgentTypingIndicator.swift`). | Absent. | Add it, driven by the core's typing event. |
| C11 | Mention autocomplete | @mentions (people **and AI agents**, inserted as `@[Name](id)`), #lists, !tasks (187-300; `Views/Components/RichTextInput.swift:371`). | Absent. | Add an autocomplete popup to the composer. Mentioning agents is how chat with agents works. |
| C12 | Attach | Paperclip menu: "From Photos" / "From Documents" / "From Camera". Several files queue as thumbnails with × to remove, then send with the text (`RichTextInput.swift:157-170, 243, 404-418`). | Paperclip opens `GetContent("*/*")`, one file, which **uploads at once** as its own comment (`TaskDetailScreen.kt:149-152`; `TaskDetailViewModel.kt:191-194`). | Offer photo, file and camera choices; queue multiple files; send with the text. |
| C13 | Composer chrome | Floating card (radius 12, shadow, 8pt margin). White field with 1pt border, radius 8, grows to 200pt. Placeholder "Add a comment...". Filled paper-plane send. Collapses while another editor holds the keyboard (`TaskDetailViewNew.swift:599-621`). | Full-width `headerBg` bar, `inputBg` field radius 12, max 4 lines, "Add a comment", Material Send icon (`TaskDetailScreen.kt:444-463`). | Match the chrome, placeholder and icon. |
| C14 | Opening files | Inline previews and a gallery preview across all task files. | Inline images, then hand-off to another app (`ui/detail/InlineImage.kt`; `TaskDetailScreen.kt:573-578`). | Acceptable as Android behaviour; consider an in-app image viewer. |

## 7. Notifications and reminders

| # | Issue | iOS | Android | What Android must change |
|---|---|---|---|---|
| N1 | Notification text | Title "Task Due Soon", body is the task title, default sound (`Core/Notifications/NotificationManager.swift:64-67`). | Title is the task title, text "Reminder" (`platform/Reminders.kt:92-93`). | Use "Task Due Soon" with the title as the body. |
| N2 | Action buttons | "Complete" (opens the app) and "Snooze 1 hour" (`NotificationManager.swift:361-372`). | Same labels; Complete runs in the background (`Reminders.kt:98-99`). | Fine. Keeping Complete in the background suits Android. |
| N3 | Tapping the notification | Opens the full-screen `ReminderView`: "These people are counting on you!", Complete, "Snooze 15 minutes", then "1 day" and "1 week" side by side. Or opens the triage deck (`Core/Notifications/ReminderPresenter.swift`; `Views/Reminders/ReminderView.swift:140-245`; `Views/Reminders/ReminderTriageView.swift`). | Opens task detail (`Reminders.kt:80-84`). | Add a reminder screen with these snooze choices, plus triage. |

## 8. Strings that differ (iOS key → Android)
- `task_detail.subtasks` "Sub tasks" → `detail_subtasks` "Subtasks"
- `comments.add_placeholder` / RichTextInput "Add a comment..." → "Add a comment"
- `task_edit.add_description` "Add a description..." → "Add a description"
- `comments.count` "Comments (%d)" → "Comments"
- `tasks.delete_task` "Delete task" → "Delete"
- `picker.add_to_lists` "Add to lists" → "—"
- `picker.morning` etc. → formatted hours
- `empty_state.*` → "Nothing here yet."

**Not built on Android yet:** `tasks.task_details`, `tasks.wont_do` / `tasks.reopen` / `tasks.wont_do_chip`, `tasks.delete_confirm`, `tasks.remove_from_board*`, `tasks.taskId.*`, `tasks.waitingOn.*`, `tasks.occurrences.*`, `task_detail.complete_*`, `task_detail.parent_task`, `task_detail.add_subtask`, `task_detail.completed_via`, `picker.all_day`, `picker.add_time`, `assignee.you` / `picker.ai_agent` / `assignee.someone_else`, `comments.empty` / `comments.delete_comment`, `attachments.*`, `share.*`, `lists.copy_to_list` / `include_comments`, `repeating.*` (monthly, yearly, mode, preview).

## 9. PARITY.md entries that are wrong
`/Users/jonparis/mycode/astrid-android/docs/PARITY.md`:
- Line 11 (task list) claims the rows match iOS's metrics. They don't: see R1–R13.
- Line 14 says swipe actions match. iOS has no swipe-to-complete (L1).
- Line 15 says custom repeat patterns are "not built yet". A partial editor now exists (`CustomRepeatDialog.kt`); this whole audit's detail section should replace that note.
- Line 16 says comments are ✅. Most comment features are missing (C1–C13).
