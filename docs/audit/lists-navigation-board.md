# iOS parity audit (2026-10-19): Navigation, lists, board, search

*Read-only comparison of astrid-ios and astrid-android source. Each finding names the iOS evidence, the Android evidence, and what Android must change. Work is tracked as AATD tasks that cite these item numbers.*

I found 70 inconsistencies in navigation and list-level features. Android has the basic skeleton: a drawer, list rows, a board with drag, a single-screen list settings page, a search screen and a sign-in screen. Most of iOS's list-level features are missing on Android, and nearly every shared surface looks or behaves differently. The core already has commands for most of the missing pieces, unused by Android: `ListCounts`, `PublicLists`, `CopyList`, `SetManualOrder`, `FilterOptions`/`SetFilter`, `MyTasksFilters`/`SetMyTasksFilters`, `CancelInvitation`, `SetInvitationRole`, `EligibleNewOwners`/`TransferListOwnership`, `CreateBoardForList`/`DeleteProject`, `AddBoardCard`, `SetListImage`, `ResolveShortcut`, `SearchUsers`/`Contacts`, `ListAgentOptions`, `LinkList`/`UnlinkList`. They are listed in `/Users/jonparis/mycode/astrid-core/crates/astrid-core/src/app/command.rs`.

**Path roots used below:**
- iOS = `/Users/jonparis/mycode/astrid-ios/Astrid App/`
- AND = `/Users/jonparis/mycode/astrid-android/app/src/main/java/com/gracefultools/astrid/`
- Main Android files: `AND/ui/AppRoot.kt`, `AND/ui/sidebar/SidebarDrawer.kt`, `AND/ui/sidebar/SidebarViewModel.kt`, `AND/ui/tasks/TaskListScreen.kt`, `AND/ui/tasks/TaskListViewModel.kt`, `AND/ui/lists/ListSettingsScreen.kt`, `AND/ui/lists/ListSettingsViewModel.kt`, `AND/ui/search/SearchScreen.kt`, `AND/ui/board/BoardScreen.kt`, `AND/ui/board/BoardViewModel.kt`, `AND/ui/signin/SignInScreen.kt`, `AND/DeepLinks.kt`, `AND/model/Rows.kt`, and `/Users/jonparis/mycode/astrid-android/app/src/main/res/values/strings.xml`.

Several iOS views are never wired into the app, so they are not parity targets: `Views/Lists/ListDefaultsView.swift`, `SaveFilterDialog.swift`, `ListAgentSettingsView.swift` and `Views/Chat/ChatToggleView.swift`. Multi-select / bulk edit doesn't exist on iOS either, so there is no gap there.

---

## A. Sidebar / drawer

1. **Brand header.** iOS shows the Astrid character image (28.8pt, corner radius 6), the server-resolved wordmark (21.6pt semibold), and a search magnifier on the far right that is the only way into search (`Core/Layout/SidebarBrandHeader.swift:26-52`, `Views/Lists/ListSidebarView.swift:141-151`). Android shows the plain text "Astrid" at 28sp bold, with no logo and no search icon (`SidebarDrawer.kt:54-57`). There is no character image asset on Android at all (`res/drawable` only has `ic_launcher_foreground`). **Change:** add the logo asset, the wordmark sizes, and a search icon in the drawer header that opens search.
2. **Account footer.**
   - iOS has a fixed floating glass card (corner radius 24, 12pt side and 8pt bottom inset) at the bottom of the sidebar (`ListSidebarView.swift:160-257`). It holds a 40pt avatar (image, or accent circle with initials) and the display name, which opens **Profile**, plus a separate `gearshape` button that opens **Settings** (`Core/Layout/SidebarAccountEntry.swift`).
   - Android has a divider and a single "Settings" row with a gear icon (`SidebarDrawer.kt:90-98`). Profile is only reachable through Settings.
   - **Change:** replace the row with the avatar/name card (opens Profile) and a trailing gear (opens Settings).
3. **"Add List" placement and icon.** On iOS, Add List is the first row inside the "Lists" section, with a `plus.circle.fill` accent icon in the 28.8pt icon column (`ListSidebarView.swift:325-347, 431-443`). On Android it is pinned below the scrolling list, outside any section, with `Icons.Outlined.Add` (`SidebarDrawer.kt:71-79`). **Change:** move it to the top of the Lists section and use a filled circle-plus icon.
4. **Create list flow.**
   - iOS opens a "New list" sheet (`Views/Lists/ListEditView.swift:30-189`) with:
     - a "List name" field (placeholder "Enter list name", 19pt medium, rounded bgSecondary)
     - a "Description" editor ("Add a description (optional)...")
     - a Members block with "Add" opening AddMemberSheet (email + role); it shows "No members added yet" when empty
     - Cancel and Create toolbar buttons, with a partial-failure error ("List created, but failed to add: …")
   - Android shows an AlertDialog titled "Add List" with only a name field (`SidebarDrawer.kt:80-89`).
   - **Change:** build the full create screen: name, description, members to invite with roles, and the "New list" title.
   - Android also selects the new list after creating it (`SidebarViewModel.kt:74-84`); iOS just dismisses (`ListEditView.swift:250`). Decide which is intended and record it.
5. **My Tasks row.**
   - iOS shows a 12pt accent **dot** (not an icon), then a `line.3.horizontal.decrease.circle` filter glyph (footnote, textMuted), then a count capsule that is always shown, even at 0. The count is incomplete tasks assigned to you (`ListSidebarView.swift:459-505, 558`).
   - Android shows a `Person` icon and a count only when it is above 0 (`SidebarDrawer.kt:60, 130-138`).
   - **Change:** use the dot and the filter glyph, and always show the count capsule.
6. **List row icon.**
   - iOS uses `ListImageView` at 12pt, centred in a 28.8pt column. It shows the list's image (`imageUrl`) when list images are on. When they are hidden (the `hide_list_images` flag or the user's `showListImages` setting) it shows a coloured `#` glyph. A dot is the fallback (`Views/Components/ListImageView.swift:22-45`, `Views/Components/ListColorGlyph.swift`, `Core/Lists/ListImagesVisibility.swift`).
   - Android always draws a 12dp colour circle, ignores `ListSummary.imageUrl`, and has no list-images setting (`SidebarDrawer.kt:133`).
   - **Change:** render list images, plus the `#` fallback driven by the same flag and setting.
7. **Count badges.**
   - iOS always shows the count, 0 included, in a capsule: callout font, textSecondary, gray 0.1 fill (or Dark.bgTertiary), 4pt padding (`Views/Lists/ListRowView.swift:68-79`). Counts come from the shared `ListTaskCount` rule plus the core's virtual-list counts (`ListSidebarView.swift:102-107, 548-561`).
   - Android shows plain 15sp textMuted text, hidden at 0 or null, using the server's `taskCount` (`SidebarDrawer.kt:138`).
   - **Change:** use the core's `ListCounts`, always show the count, and match the capsule style.
8. **Privacy indicator.** iOS rows show a `globe` (accent colour) for PUBLIC and `person.2.fill` (green) for SHARED (`ListRowView.swift:62-65, 91-108`). Android shows nothing. **Change:** add the privacy icons before the count.
9. **Long-press to favourite.** On iOS, long-pressing a list row toggles favourite, with a medium haptic (`ListRowView.swift:86-88, 110-126`). Android has no long-press. **Change:** long-press (or a context menu) to favourite or unfavourite.
10. **Featured / public lists.**
    - iOS sidebar has a "Public Shared Lists" section and a "Public Lists" section. Each shows at most 2 lists, then "See all collaborative lists →" or "See all suggested lists →" (`ListSidebarView.swift:349-429`). Data comes from `getPublicLists(limit:10, sortBy:"popular")` split by `PublicListSections`.
    - Android has none of this, and no public list browser.
    - **Change:** add both sections and the browser (see section F).
11. **Section headers.** iOS uses `List.listStyle(.sidebar)` with title-case "Favorites" and "Lists"; only the featured headers are uppercase caption (`ListSidebarView.swift:84, 305, 345, 354-358`). Android uppercases every section at 13sp semibold (`SidebarDrawer.kt:104-108`). **Change:** title-case Favorites and Lists; keep uppercase only for the featured headers.
12. **Drawer background and width.**
    - iOS sidebar background is the theme's bgPrimary: cyan `rgb(136,220,248)` in Ocean, white in Light, `#111827` in Dark (`ListSidebarView.swift:41-53, 86`). iPhone width is 85% of the screen (`Views/MainTabView.swift:214`).
    - Android uses `colors.bgSecondary`, which is off-white `#F9FAFB` even in Ocean, and a fixed 300dp width (`SidebarDrawer.kt:52`).
    - **Change:** use bgPrimary, and size the drawer to about 85% of the width, capped for tablets.
13. **Row metrics and selection.** iOS icons sit in a 28.8pt column with 8pt spacing so names line up under the wordmark. Rows have 8pt vertical padding and 4pt trailing. Selection fill is blue at 0.1 opacity (Dark: bgTertiary), with a 0.98 press scale (`ListRowView.swift:4-22, 47-84`). Android uses 12pt horizontal / 10dp vertical padding, a 12dp gap after the icon, and bgSelected (`SidebarDrawer.kt:120-137`). **Change:** align to the logo column and iOS's paddings.
14. **Local-only users (sign-in prompt).** iOS's footer shows "Sign In" / "Sign in to sync" with a `person.crop.circle.badge.plus` avatar in local mode (`ListSidebarView.swift:164-222`). Android has no local mode. This only matters if local mode is adopted (see section I).

## B. List header / toolbar

15. **Header shape.**
    - iOS uses a floating rounded card (corner radius 12) with a black 0.08-opacity shadow (radius 4, y 2), inset 8pt each side and 8pt from the top. Its background is white in Ocean, thin glass material in Light, and Dark.headerBg in Dark (`Views/Tasks/TaskListView.swift:435-552, 1306-1326`).
    - Android uses a flat, full-width `headerBg` row (cyan in Ocean) with a hard-coded `top = 36.dp` instead of status-bar insets (`TaskListScreen.kt:141-145`).
    - **Change:** make it the floating card with theme-correct fills, and use `statusBarsPadding`.
16. **Menu icon and title.**
    - iOS uses a custom two-line hamburger: 19.2×2pt lines, 5pt gap, in a 24pt frame, 22pt leading padding (`Views/Components/HamburgerMenuIcon.swift`). The tap target is a 120×60 overlay that includes the title (`TaskListView.swift:535-551`). The title is headline (17 semibold).
    - Android uses `Icons.Filled.Menu` and a 22sp title (`TaskListScreen.kt:146-154`).
    - **Change:** use the two-line icon, a 17sp semibold title, and the larger tap area.
17. **Search in the header.** iOS has no search button in the list header; search lives in the sidebar header (`TaskListView.swift:449-525`). Android puts Search in the header (`TaskListScreen.kt:167`). **Change:** move search into the drawer header (item 1). Keeping a header icon would be a documented divergence.
18. **View toggle (list, chat, board).**
    - iOS has one `ViewModeRotatorButton`. It cycles list → messages → board → list, skipping board when the list has no project. The icon shows the *next* mode: `list.bullet`, `bubble.left.and.bubble.right` or `rectangle.split.3x1` (`Views/Chat/ViewModeRotatorButton.swift`, `Core/Layout/HeaderViewToggle.swift:45-57`). Messages replace the list in place via `ChatPanelView` (`TaskListView.swift:568-571`).
    - Android has separate Chat and board-toggle icons (`ViewKanban`/`ViewList`), and Chat navigates to a separate screen (`TaskListScreen.kt:155-166`, `AppRoot.kt:122-125, 191-196`).
    - **Change:** one rotator with next-mode icons, with chat shown in place.
19. **Chat on My Tasks.** iOS offers messages on My Tasks through a virtual channel `virtual-chat:<uid>:my-tasks` (`Views/Chat/ChatPanelView.swift:218-221`, `TaskListView.swift:477-484`). Android hides chat for every virtual list (`AppRoot.kt:125`). **Change:** allow chat on My Tasks using `ResolveChatChannel` with the virtual key.
20. **Default to board.** iOS switches to board whenever a newly selected list has a project (`TaskListView.swift:742-746`). Android defaults to list and remembers a per-list in-memory toggle (`AppRoot.kt:64-65, 110`); the code comment there wrongly claims iOS behaves the same. **Change:** default to board on selection when a board exists.
21. **Settings button icon and gating.** iOS shows a `gearshape` for any list the user is a member of, **including saved-filter (virtual) user lists** (`TaskListView.swift:162-168, 515-524`). Android shows `MoreVert` (which reads as an overflow menu) and hides it for any `isVirtual` list (`TaskListScreen.kt:168-170`, `AppRoot.kt:124`). As a result, saved filters on Android can't be edited, unsaved, left or deleted. **Change:** use a gear icon, and show it for saved filters and other member lists.
22. **My Tasks filter button.** On My Tasks, iOS shows a `line.3.horizontal.decrease.circle` button that opens the "Sort & Filters" sheet (`TaskListView.swift:506-514`, `Views/Tasks/MyTasksFilterSheet.swift`). Android has nothing for My Tasks. **Change:** add the button and sheet (item 28).
23. **My Tasks title suffix.** iOS titles My Tasks as "My Tasks - Today !! !" when filters are active. Date words come from today, this_week, this_month, overdue and no_date. Priority marks are `!!!`, `!!`, `!` and `○`, coloured red, orange, blue and gray (`TaskListView.swift:171-355`). Android shows a plain "My Tasks" (`AppRoot.kt:107`). **Change:** build the suffixed title with coloured priority marks.
24. **Copy List for featured lists.** When viewing a featured public list, iOS shows a `doc.on.doc` accent button with a spinner, calling `copyList`. On failure it shows "Couldn't copy that list. Please try again." (`TaskListView.swift:450-469, 1135-1151`). Android has nothing. **Change:** add it alongside featured viewing (section F).
25. **Quick-add visibility.**
    - iOS hides quick-add when the user can't add tasks (`ListPermissions.canAddTasks`, with a Bugs list exception), and in board and messages modes (`TaskListView.swift:135-157, 616-630`).
    - Android always shows `QuickAddBar`, **including on the board** (`TaskListScreen.kt:106-133`).
    - **Change:** apply the permission check, and hide the bar in board mode (columns get their own add footer, item 45).
26. **Quick-add "+" opens the new task.** On iOS, tapping the plus button creates the task and navigates to it (or to the iPad panel); pressing Return only creates (`Views/Tasks/QuickAddTaskView.swift:638-643`, `TaskListView.swift:619-628`). Android's send icon only creates (`TaskListScreen.kt:235-237`). **Change:** make the button create and open; keep IME Done as create-only.

## C. Sort & filters

27. **List Filters tab.**
    - iOS (`Views/Lists/ListSortFiltersTab.swift:44-253`) has:
      - **Sort:** Auto, Manual, Due Date, Priority, Created Date, Recently Completed
      - **Filters:** Task Completion (Incomplete + recently completed, All, Completed, Incomplete); Assignee (All, Me, Not Me, Unassigned, plus members); Due Date (All, Overdue, Today, This Week, This Month, No Date); Priority (All, !!!, !!, !, ○); Assigned By; Repeating (All, Not repeating, Daily, Weekly, Monthly, Yearly, Custom); List Membership (All tasks, In any list, Not in any list)
      - **Other controls:** "Clear All Filters"; a "Show subtasks" toggle with its footer text; a Favorite toggle (`star`/`star.fill` in yellow) with "This list will appear at the top of your lists"; a "Saved Filter" toggle with its description
    - Android has none of this except a bare Favorite switch.
    - **Change:** build the tab from the core's `FilterOptions`/`SetFilter`, plus the subtask, favorite and saved-filter toggles.
28. **My Tasks Sort & Filters sheet.** iOS (`Views/Tasks/MyTasksFilterSheet.swift:17-96`) offers sort (Auto, Priority, Due Date, Who, Completed, Incomplete, Manual), completion, due date and priority, plus Clear All Filters and Done. Settings sync across devices. Android has no equivalent. **Change:** implement it using `MyTasksFilters`/`SetMyTasksFilters` (or `FilterOptions` with the My Tasks virtual id).
29. **Manual reorder.** When sort is manual, iOS lets you drag rows to reorder (`.onMove`); hidden tasks are appended newest-first and the order is written to the list or to My Tasks preferences (`TaskListView.swift:888, 980-1045`). Android has no reorder. **Change:** add long-press drag reordering in manual sort, calling `SetManualOrder`.
30. **Re-nesting by drag.** iOS has three distinct drop targets while a drag is in flight (`TaskListView.swift:1342-1466`, `Core/Filters/DragNesting.swift`):
    - onto a row, to nest under it (accent 2pt border)
    - onto an insertion line above a row, to promote to top level
    - onto a leading outdent band with an `arrow.left.to.line` glyph, to move out one level

    Android has none of these. **Change:** implement them, with TalkBack actions as alternatives.

## D. List settings

31. **Structure.**
    - iOS opens a modal "List Settings" sheet with a segmented control: Filters (`line.3.horizontal.decrease.circle`), Members (`person.2`), and Admin (`gearshape`, admins only). It has a "Done" button (`Views/Lists/ListSettingsModal.swift:71-120`).
    - Android pushes a single "List settings" screen (`ListSettingsScreen.kt:70-143`).
    - **Change:** use the three-tab structure (Material tabs are fine) and the title "List Settings".
32. **Description field.** iOS Admin has a "List Information" section with a name field and a multiline description, both saved as you type (`Views/Lists/ListAdminTab.swift:95-105`). Android has only a name field, saved on focus loss (`ListSettingsScreen.kt:78-83`). **Change:** add the description, plus the section header.
33. **List image.** When images are shown, iOS has a 64pt preview with "List Image" / "Choose a custom image or icon" and a "Change" button that opens `ImagePickerView` full-screen (`ListAdminTab.swift:111-138, 498-502`). Android has nothing. **Change:** add an image picker row (core `SetListImage`), hidden when list images are hidden.
34. **Colour picker.**
    - iOS has a "List Color" row of the palette plus a custom ColorPicker. Swatches are 28pt with a white checkmark when selected, the row is evenly distributed, and custom picks are debounced 400ms (`Views/Components/ListColorSwatches.swift`).
    - Android shows 36dp circles in a FlowRow with a 3dp textPrimary ring when selected, and no custom colour (`ListSettingsScreen.kt:90-101`).
    - **Change:** add the checkmark style and a custom colour option, under a "List Appearance" section.
35. **Default task settings.** iOS (`ListAdminTab.swift:150-221`) offers:
    - Priority via `PriorityButtonPicker`
    - Assignee: Task Creator, Unassigned, or a member
    - Due Date: None, Today, Tomorrow, Next Week, Next Month
    - Due Time: All day, 9:00 AM, 12:00 PM, 2:00 PM, 5:00 PM, 6:00 PM, 8:00 PM
    - Repeating: Never, Daily, Weekly, Monthly

    Android has none. **Change:** add this section (an `UpdateList` with the default fields).
36. **Project board.** iOS has a "Project board" section with "Create Board" (with spinner) or "Disable Board" (in red), with footers. Disabling asks for confirmation ("Disable board?" … "Disable") and errors appear inline (`ListAdminTab.swift:399-435, 511-518, 645-696`). Android can't create or remove a board. **Change:** add it using `CreateBoardForList`/`DeleteProject`.
37. **Advanced: recently completed window.** iOS has a "Recently completed" picker of presets, with a "Since" date picker for the specific-date preset (`ListAdminTab.swift:437-464`). Android has nothing. **Change:** add it.
38. **AI coding agent and external sync.** iOS has a GitHub repository picker (when AI providers exist) and an "External sync" section linking or unlinking GitHub Issues and Google Tasks for this list (`ListAdminTab.swift:223-397`). Android has nothing. **Change:** add both, using `ListAgentOptions` and `LinkList`/`UnlinkList`.
39. **Danger zone and delete confirmation.**
    - iOS has a "Danger Zone" section with a `trash` icon and "Delete list". Its alert is titled "Delete list", says "Are you sure you want to delete this list? This action cannot be undone.", and offers Cancel and a destructive "Delete" (`ListAdminTab.swift:466-475, 503-510`).
    - Android shows an untitled dialog: "Delete this list? Its tasks stay in any other lists they are in." with a "Delete list" button (`ListSettingsScreen.kt:147-154`, `strings.xml:108`).
    - **Change:** match the section, icon, title, copy and button label.
40. **Privacy control.**
    - iOS (Members tab, admins only) has a single button. It shows "Make Public" (`globe`, green) unless the list is public, in which case it shows "Make Private" (`lock`, orange), which sets SHARED. It is followed by "Anyone with the link can view this list" or "Only members can view this list" (`Views/Lists/ListMembershipTab.swift:465-507`).
    - Android shows a three-way Private / Shared / Public chip row under Admin (`ListSettingsScreen.kt:102-112`).
    - **Change:** use iOS's public/private toggle, its copy, and its placement.
41. **Member rows.**
    - iOS lists the owner first with a 32pt avatar, name, email and an "Owner" label. Members show avatar, name and email; tapping opens their user profile. Admins get a role label plus a vertical-ellipsis menu ("Make Admin"/"Make Member", destructive "Remove" with icons) (`ListMembershipTab.swift:132-261`).
    - Android shows name-only rows. Tapping the role text toggles it, and there is an inline red "Remove" button (`ListSettingsScreen.kt:115-131`).
    - **Change:** add avatars, emails, the owner row, the overflow menu and profile navigation.
42. **Pending invitations.** iOS shows an `envelope` avatar, the email, "Pending" in orange, then "· Admin/Member", and an `xmark.circle.fill` to cancel (`ListMembershipTab.swift:263-304`). Android doesn't distinguish invitations, even though `Member.isPending` and `isInvitation` exist (`Rows.kt:102-103`), and can't cancel them. **Change:** render invitations separately and cancel them with `CancelInvitation`.
43. **Add member.**
    - iOS has an "Add Member" button (`person.badge.plus`, accent) that opens `AddMemberSheet`. The sheet has an email field with contacts autocomplete ("Searching contacts..."), a role picker, suggested collaborators, and an "Add Collaborators / Upload your contacts" prompt; the title is "Add Member" (`ListMembershipTab.swift:307-319`, `Views/Lists/AddMemberSheet.swift:80-185`).
    - Android has an inline "Invite by email" field and button, always with role `member` (`ListSettingsScreen.kt:133-139`, `ListSettingsViewModel.kt:53-57`).
    - **Change:** add the sheet with a role picker and suggestions (`SearchUsers`/`Contacts`).
44. **Share List.** iOS has a "Share" section with a "Share List" button (`square.and.arrow.up`) and the footer "Generate a shareable link for this list". It opens `ShareTargetView`, which shows a privacy notice, generates a shortcode link, and offers copy and the system share sheet (`ListMembershipTab.swift:321-337`, `Views/Components/ShareTargetView.swift:29-140`). Android has nothing. **Change:** add list sharing using the system share sheet.
45. **AI agents section.** For admins, iOS shows "AI Agents" / "AI Coding Agents" (purple `cpu`), each agent with a 40pt avatar, a description, and Add/Remove pills (`ListMembershipTab.swift:339-463`). Android has nothing. **Change:** add it.
46. **Leave confirmation.** iOS shows "Leave List" (`rectangle.portrait.and.arrow.right`) and confirms with an alert: "Are you sure you want to leave this list? You will need to be re-invited to rejoin." (`ListSettingsModal.swift:121-128`, `ListMembershipTab.swift:518-526`). **Android leaves immediately with no confirmation** (`ListSettingsScreen.kt:141`), and its copy is "Leave list". **Change:** add the confirmation, the icon and the "Leave List" copy.
47. **Transfer ownership.** iOS shows owners a "Transfer Ownership" section: a successor picker (pre-selected), "Transfer and leave", and a confirmation dialog. It covers the unavailable and no-eligible-owner states (`ListMembershipTab.swift:509-650`). Android has nothing, so owners have no way out. **Change:** implement it with `EligibleNewOwners`/`TransferListOwnership`.
48. **Non-admin view.** Non-admin iOS members see Filters and Members, the members list read-only, plus Share and Leave (`ListSettingsModal.swift:74-108`). Android non-admins see a disabled name field and a Favorite switch. **Change:** follow from items 31–47.
49. **Local-only users.** iOS shows "List sharing requires an account" with a "Sign in to share lists" CTA (`ListMembershipTab.swift:75-116`). This only applies if Android adopts local mode.

## E. Board / kanban

50. **Board errors are dropped.** iOS shows a "Couldn't move task" alert with an OK button (`Views/Board/ProjectStatusBoardView.swift:226-230`). Android sets `BoardState.error` (`BoardViewModel.kt:47, 57, 71, 87`) but nothing displays it: `AppRoot` only passes `list.error` to the snackbar (`AppRoot.kt:120-146`). **Change:** show board errors.
51. **Column layout on phones.**
    - iOS shows `boardColumnsVisible = floor(width/300)`, clamped to 1…5, columns per screen, so a phone shows one full-width column. It pages one column per swipe, starts on Inbox, gives a light haptic on snap, and scrolls to the open task's column (`ProjectStatusBoardView.swift:113-200`, `Core/Board/ProjectStatus.swift:108-113`).
    - Android uses fixed 300dp columns in a free-scrolling LazyRow (`BoardScreen.kt:99-131, 163`).
    - **Change:** size columns by that rule, snap one column at a time, and scroll to the selected task's column.
52. **Column chrome.**
    - iOS columns have a frame colour (white 0.8 in Ocean, Dark.headerBg, or bgPrimary) around a recessed interior (cyan, Dark.bgPrimary, or bgTertiary), 6pt side walls and corner radius 12. The header has the name in headline, a count capsule, and the description in caption over 2 lines (`Views/Board/BoardColumnView.swift:164-186, 445-482`).
    - Android uses a bgSecondary 0.6-alpha box, a 17sp name, a plain "  N" count, and no description (`BoardScreen.kt:161-171`).
    - **Change:** match the frame/well colours, count capsule and description.
53. **Cards.** iOS cards have corner radius 8 with a separator stroke of 0.5 (2.5pt accent when selected). The fill is white 0.8 in Ocean (1.0 when selected) or Dark.bgSecondary, with 6pt spacing (`Views/Board/BoardTaskCardView.swift:38-95`, `BoardColumnView.swift:235-242`). Android reuses list rows with 8dp spacing and no selected state (`BoardScreen.kt:172-192`). **Change:** add card styling and a selected-card highlight in two-pane.
54. **Drop feedback.** iOS shows a 3pt accent insertion line at the target slot and a 3pt accent ring around the hovered column (`BoardColumnView.swift:279-291, 330-344`). Android shows only the floating card. **Change:** add the insertion line and the column ring.
55. **Per-column add.** Every non-Done column on iOS has an inline quick-add footer with the column's `statusRole` (`BoardColumnView.swift:206-208, 258-270`). Android has none (and shows the global bar instead, item 25). **Change:** add a footer per column using `AddBoardCard`.
56. **Empty column.** iOS shows the Astrid speech bubble "Ready to capture your thoughts? Create your first task to get started!" with a minimum height of 260 (`BoardColumnView.swift:398-418`). Android shows an empty column. **Change:** add the empty state.
57. **Edge swipe opens the sidebar.** On iOS, swiping right at the leftmost column opens the sidebar (`ProjectStatusBoardView.swift:203-223`). The Android drawer gesture competes with the LazyRow. **Change:** confirm the edge swipe still opens the drawer on the board.
58. **Board full screen (tablets).** iOS has a board full-screen toggle (`arrow.up.left.and.arrow.down.right`) that drops the picker and messages panes and persists (`TaskListView.swift:486-503`). Android has none. **Change:** add it in the wide layout.

## F. Featured / public list browsing

59. **Featured Lists browser.** iOS's "Featured Lists" screen (large title) lists public lists (`getPublicLists(limit:50, popular)`) with:
    - a 16pt icon, headline name, a `globe` icon, and `person.2` in green for collaborative lists
    - a two-line description and "%d tasks"
    - "View & Add" (opens the list) or "Copy" / "Copying..."
    - an Astrid empty state with pull-to-refresh

    Source: `Views/Lists/PublicListBrowserView.swift:10-183`. Android has nothing. **Change:** build it with `PublicLists`/`CopyList`.
60. **Viewing a featured list.** On iOS, your own tasks are merged with other people's public tasks (`TaskListRows.mergeFeatured`). Tasks you didn't create are read-only, the leading control copies a task, and the rotator is hidden (`TaskListView.swift:942-952, 1153-1177, 1227-1241`). Android can't do this. Android's `RowActions` already handles the core's `copy` action. **Change:** add a featured-list mode.

## G. Search

61. **Search screen.**
    - On iOS, search is a main-pane mode. The floating header keeps the hamburger and has a `magnifyingglass` + field (placeholder "Search tasks...", headline font, autocorrect off) and an `xmark.circle.fill` clear button. It auto-focuses after 0.3s (`TaskListView.swift:367-432`).
    - Android pushes a screen with a back arrow, a "Search tasks" field, and no clear button (`SearchScreen.kt:46-59`, `strings.xml:65`).
    - **Change:** add the magnifier, the clear button, the "Search tasks..." copy, and the floating-header styling.
62. **Search empty states.** iOS shows the Astrid bubble with "Search tasks..." for an empty query and "Ready to capture your thoughts? …" for no results (`TaskListView.swift:1068-1080`). Android shows the plain text "No matching tasks" (`SearchScreen.kt:60-62`). **Change:** use the Astrid bubble and iOS's copy.
63. **Tapping the checkbox in search results opens the task instead of completing it.** iOS completes via `onToggle` (`TaskListView.swift:834-842`). Android passes `onLeading = { onOpenTask(row.id) }` (`SearchScreen.kt:67`). **Change:** route the leading control through the core's `action`, as in the list.
64. **Subtasks in results.** iOS splices subtasks under results using the account and list subtask settings (`TaskListView.swift:922-932`). Android shows a flat list capped at 100 (`SearchViewModel.kt:41`). **Change:** request the spliced results from the core.

## H. Empty, loading and refresh states

65. **Empty list state.**
    - iOS uses `EmptyStateView`: the Astrid character at 160pt, plus a speech bubble (semibold body, radius 16, 2pt border, left-pointing tail, white/bgSecondary fill) (`Views/Components/EmptyStateView.swift`, `Views/Components/AstridSpeechBubble.swift`). The message depends on the list (`TaskListView.swift:1068-1126`):
      - My Tasks: "Welcome to Astrid! Add your first task to get started."
      - Today: "Nothing scheduled for today!…"
      - Public list: "Share your ideas with the world!…"
      - Shared list: "Start collaborating!…"
      - Private list: `Let's fill up "<name>"!…`
      - Default: "Ready to capture your thoughts?…"
    - Android shows the centred text "Nothing here yet." (`TaskListScreen.kt:114-119`).
    - **Change:** port the component and the per-list-type messages.
66. **Loading state.** iOS shows the Astrid bubble with "Loading your tasks..." until the first load completes (`TaskListView.swift:574-575, 788-798`). Android shows a blank pane while `loading` is true. **Change:** add the loading state.
67. **Pull-to-refresh scope.** On iOS, pull-to-refresh runs a full sync and also schedules GitHub and Google Tasks sync (`TaskListView.swift:712-722`). Android runs only the core `sync` (`TaskListViewModel.kt:93-98`). **Change:** also trigger external sync, if exposed (`SyncExternal`).

## I. Onboarding / first run

68. **Launch screen and welcome.**
    - On iOS, while auth is being checked, a `SplashView` looks like an empty list: floating header, Astrid "Loading your tasks...", and a blank quick-add bar. The first unauthenticated run shows `WelcomeChoiceView`: a 120pt character, "Welcome to Astrid!", "Your intelligent task manager", a "Sign in" button and a "Use without account" button (`/Users/jonparis/mycode/astrid-ios/AstridApp.swift:237-273`, `Views/Components/SplashView.swift`, `Views/Onboarding/WelcomeChoiceView.swift:29-105`).
    - Android shows a spinner on the sign-in screen and has no welcome screen and no local mode (`SignInScreen.kt:56-57`, `MainActivity.kt:39-56`).
    - **Change:** add the splash and the welcome screen. Local mode is a decision to record in PARITY.md.
69. **Sign-in header.** iOS shows an 88pt character image beside the server-resolved wordmark (34 bold) and slogan (16), provider buttons in the server's order, an "or" divider, and "Use without account" with its explanation (`Views/Authentication/LoginView.swift:40-135`). Android shows a 44sp wordmark, the "Get it done!" tagline, and a fixed provider order (`SignInScreen.kt:51-79`). **Change:** add the character image, brand capabilities and provider order.

## J. Navigation, deep links and tablets

70. **Deep links to lists.** iOS opens lists from `astrid://lists/<id>`, `https://…/lists/<id>`, and shortcodes `/s/<code>` that resolve to a task or list (`Core/Services/DeepLinkManager.swift:70-180`). Android handles task links only, and uses `astrid://task/` where iOS uses `astrid://tasks/` (`DeepLinks.kt:23-28`). **Change:** add list links and shortcodes (`ResolveShortcut`), and accept `astrid://tasks/`.

**Tablet layout (also under J):**
- **Empty detail pane.** On iPad the detail panel is an overlay that appears only when a task is selected, so the list keeps its full width. Tapping the same task again closes it, and the selected row is highlighted (`Views/Tasks/iPadTaskManagerView.swift:380-436`, `TaskListView.swift:866-876`). Android always reserves a 1.2-weight pane showing "Select a task", can't toggle it off, and has no row highlight (`AppRoot.kt:148-166`). **Change:** show the pane only when a task is selected, toggle it off on a second tap, and highlight the selected row.
- **Messages pane.** iPad landscape has three columns: sidebar at 28%, the list, and a messages pane at 45% of what remains, collapsible to a 48pt rail (`Core/Layout/iPadPaneLayout.swift:14-54`). Android has no messages pane. **Change:** add it on wide windows.

**Visual and copy nits:**
- **List margins.** iOS rows are inset 8pt on each side, and the first row gets 8pt at the top (`TaskListView.swift:860-865`). Android uses 12dp side padding (`TaskListScreen.kt:122`). **Change:** use 8dp.
- **Copy.** iOS uses "List Settings", "Leave List" and "Search tasks..." (`en.lproj/Localizable.strings`). Android uses "List settings", "Leave list" and "Search tasks" (`strings.xml:65, 92, 106`). **Change:** match iOS's wording.

**PARITY.md is out of date:** `/Users/jonparis/mycode/astrid-android/docs/PARITY.md:21,25,30-31` still lists board drag, tablet layout and the Agents/Profile/Connections screens as pending, but all three have shipped. It also marks list settings, sharing and members as fully done, which items 27–47 show they are not.
