package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The sizes Android draws a person's face at (ADEV-196). iOS sizes each avatar's frame by hand and
 * core's tokens have no avatar scale yet, so the steps are named here after the iOS views they match;
 * when core adds a scale, these read it.
 */
object AvatarSize {
    /** The assignee on a task row (iOS InlineAssigneePicker's chip). */
    val xxs = 22.dp
    /** Who is on a task; list insights contributors (iOS InlineAssigneePicker rows, autocomplete). */
    val xs = 24.dp
    /** The task's creator in details (iOS TaskDetailViewNew). */
    val sm = 28.dp
    /** Comments, list members, pickers, a reminder's people (iOS ChatMessageBubble, ListMembershipTab). */
    val md = 32.dp
    /** The assignee picker (iOS QuickAddPickerSheet). */
    val lg = 36.dp
    /** The sidebar account and agent members (iOS ListSidebarView, ListMembershipTab agents). */
    val xl = 40.dp
    /** Account settings (iOS AccountSettingsView). */
    val account = 60.dp
    /** A user's profile (iOS UserProfileView). */
    val profile = 80.dp
    /** Edit profile (iOS EditProfileView). */
    val profileEdit = 120.dp
}
