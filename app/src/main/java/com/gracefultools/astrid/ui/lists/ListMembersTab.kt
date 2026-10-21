package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.Member
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.detail.PickRow
import com.gracefultools.astrid.ui.detail.PickerSheet
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles

/** iOS's system `.orange` and `.purple`, which the Members tab uses for privacy and agents. */
private val Orange = Color(0xFFFF9500)
private val Purple = Color(0xFFAF52DE)

/**
 * iOS ListMembershipTab: the roster (owner first, members, pending invitations), Add Member, Share,
 * AI Agents and List Privacy for admins, then Leave List — or, for the owner, Transfer ownership.
 */
@Composable
internal fun ListMembersTab(state: ListSettingsState, vm: ListSettingsViewModel, onOpenProfile: (String) -> Unit = {}) {
    val settings = state.settings ?: return
    val colors = AstridTheme.colors
    var adding by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(settings.canTransferOwnership) { if (settings.canTransferOwnership) vm.loadTransfer() }
    LaunchedEffect(settings.canManageList) { if (settings.canManageList && state.agentOptions == null) vm.loadAgentOptions() }

    FormColumn {
        FormSection(stringResource(R.string.lists_members)) {
            var first = true
            val divider: @Composable () -> Unit = { if (!first) FormDivider(); first = false }
            state.owner?.let { owner ->
                divider()
                PersonRow(owner, onOpen = { onOpenProfile(owner.id) }) { Text(stringResource(R.string.lists_owner), style = TextStyles.caption, color = colors.textMuted) }
            }
            state.otherMembers.forEach { member ->
                divider()
                MemberRow(member, settings.canManageMembers, vm, onOpenProfile)
            }
            if (settings.canManageMembers) state.pendingInvitations.forEach { invite ->
                divider()
                FormRow {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Email, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(invite.email, style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                            Text(stringResource(R.string.lists_pending), style = TextStyles.caption2, color = Orange)
                            Text("·", style = TextStyles.caption2, color = colors.textSecondary)
                            Text(roleLabel(invite.role), style = TextStyles.caption2, color = colors.textSecondary)
                        }
                    }
                    IconButton(onClick = { vm.cancelInvitation(invite.email) }) {
                        Icon(Icons.Filled.Cancel, stringResource(R.string.lists_cancel_invitation, invite.email), tint = Color.Gray, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (settings.canManageMembers) {
            FormSection {
                FormButtonRow(Icons.Outlined.PersonAdd, stringResource(R.string.lists_add_member), colors.accent) { adding = true }
            }
        }

        FormSection(stringResource(R.string.actions_share), footer = stringResource(R.string.lists_share_list_description)) {
            FormButtonRow(Icons.Outlined.IosShare, stringResource(R.string.lists_share_list), colors.accent) { sharing = true; vm.requestShareLink() }
        }

        // AI Agents — admins, when the account has agents (listAgentOptions).
        val agents = state.agentOptions?.agents.orEmpty()
        if (settings.canManageList && agents.isNotEmpty()) {
            val github = state.agentOptions?.githubConnected == true
            FormSection(
                stringResource(if (github) R.string.lists_ai_coding_agents else R.string.lists_ai_agents),
                footer = if (github) null else stringResource(R.string.lists_connect_github_description),
                header = { Icon(Icons.Outlined.Memory, null, tint = Purple, modifier = Modifier.size(14.dp)) },
            ) {
                Text(
                    stringResource(R.string.lists_available_agents), style = TextStyles.caption, color = colors.textSecondary,
                    modifier = Modifier.padding(start = Spacing.s16, end = Spacing.s16, top = Spacing.s12),
                )
                agents.forEach { agent ->
                    val member = state.isMember(agent.id)
                    FormRow(Modifier.padding(vertical = Spacing.s4)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Purple.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            if (agent.image != null) Avatar(agent.image, initialsOf(agent.name), 40.dp)
                            else Icon(Icons.Outlined.Memory, null, tint = Purple, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                            Text(agent.name, style = TextStyles.body, color = colors.textPrimary)
                            Text(stringResource(R.string.lists_default_agent_description), style = TextStyles.caption2, color = colors.textMuted, maxLines = 2)
                        }
                        when {
                            member -> Pill(stringResource(R.string.actions_remove), Color.Red) { vm.removeAgent(agent.id) }
                            // An invitation needs the agent's address; listAgentOptions does not answer it yet.
                            agent.email != null -> Pill(stringResource(R.string.actions_add), colors.accent) { vm.addAgent(agent.email) }
                        }
                    }
                }
            }
        }

        // List Privacy — admins: one button, Make Public or Make Private (which sets SHARED).
        if (settings.canManageList) {
            val isPublic = settings.privacy.equals("PUBLIC", ignoreCase = true)
            FormSection(stringResource(R.string.lists_list_privacy)) {
                if (isPublic) FormButtonRow(Icons.Outlined.Lock, stringResource(R.string.lists_make_private), Orange) { vm.togglePublic() }
                else FormButtonRow(Icons.Outlined.Public, stringResource(R.string.lists_make_public), Success) { vm.togglePublic() }
                Text(
                    stringResource(if (isPublic) R.string.lists_public_description else R.string.lists_private_description),
                    style = TextStyles.caption2, color = colors.textSecondary,
                    modifier = Modifier.padding(start = Spacing.s16, end = Spacing.s16, bottom = Spacing.s12),
                )
            }
        }

        when {
            settings.canLeave -> FormSection {
                FormButtonRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.lists_leave_list), Error) { confirmLeave = true }
            }
            settings.canTransferOwnership -> TransferSection(state, settings.name, vm)
        }
    }

    if (adding) {
        AddMemberSheet(
            state.suggestions, state.searching, onQuery = vm::search,
            onAdd = { email, role -> vm.invite(email, role); vm.clearSuggestions(); adding = false },
            onDismiss = { vm.clearSuggestions(); adding = false },
        )
    }
    if (sharing) ShareListSheet(settings.name, settings.privacy, state.shareLink, state.shareFailed) { sharing = false }
    if (confirmLeave) {
        ConfirmAlert(
            stringResource(R.string.lists_leave_list), stringResource(R.string.lists_leave_confirm), stringResource(R.string.lists_leave_list),
            onConfirm = vm::leave, onDismiss = { confirmLeave = false },
        )
    }
}

/** iOS's owner-only leave: pick a successor, then "Transfer & Leave" after a confirmation. */
@Composable
private fun TransferSection(state: ListSettingsState, listName: String, vm: ListSettingsViewModel) {
    val colors = AstridTheme.colors
    val transfer = state.transfer ?: return
    when (transfer.availability) {
        "notPermitted" -> Unit
        "noEligibleOwners" -> FormSection {
            Text(stringResource(R.string.lists_transfer_ownership_none), style = TextStyles.caption2, color = colors.textSecondary, modifier = Modifier.padding(Spacing.s16))
        }
        "available" -> {
            var successor by remember(transfer) { mutableStateOf(transfer.owners.firstOrNull()?.id.orEmpty()) }
            var choosing by remember { mutableStateOf(false) }
            var confirming by remember { mutableStateOf(false) }
            FormSection(stringResource(R.string.lists_transfer_ownership), footer = stringResource(R.string.lists_transfer_ownership_prompt, listName)) {
                FormPickerRow(stringResource(R.string.lists_transfer_ownership_select), transfer.owners.firstOrNull { it.id == successor }?.display.orEmpty()) { choosing = true }
                FormDivider()
                FormButtonRow(
                    Icons.Outlined.HowToReg, stringResource(R.string.lists_transfer_and_leave), Error,
                    enabled = successor.isNotEmpty() && !state.transferring,
                ) { confirming = true }
            }
            if (choosing) PickerSheet(stringResource(R.string.lists_transfer_ownership_select), { choosing = false }) {
                transfer.owners.forEach { person -> PickRow(person.display, person.id == successor) { successor = person.id; choosing = false } }
            }
            if (confirming) {
                ConfirmAlert(
                    stringResource(R.string.lists_transfer_ownership), stringResource(R.string.lists_transfer_ownership_confirm, listName),
                    stringResource(R.string.lists_transfer_and_leave),
                    onConfirm = { vm.transferOwnership(successor) }, onDismiss = { confirming = false },
                )
            }
        }
        else -> FormSection {
            Text(stringResource(R.string.lists_owner_cannot_leave_yet), style = TextStyles.caption2, color = colors.textSecondary, modifier = Modifier.padding(Spacing.s16))
        }
    }
}

@Composable
private fun roleLabel(role: String): String =
    stringResource(if (role.equals("admin", ignoreCase = true)) R.string.lists_role_admin else R.string.lists_role_member)

/**
 * A person on the roster: 32dp avatar, name, email (or "No email"), and a trailing control. The
 * face and the name open their profile, as iOS's member list does (AATD-69); the trailing control
 * keeps its own tap.
 */
@Composable
private fun PersonRow(person: UserSummary, onOpen: () -> Unit, trailing: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    FormRow {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(person.image, initialsOf(person.name, person.email), 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(person.display, style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                Text(person.email ?: stringResource(R.string.profile_no_email), style = TextStyles.caption2, color = colors.textSecondary, maxLines = 1)
            }
        }
        trailing()
    }
}

/** A member row; admins get the role label and a vertical-ellipsis menu (role, destructive Remove). */
@Composable
private fun MemberRow(member: Member, canManage: Boolean, vm: ListSettingsViewModel, onOpenProfile: (String) -> Unit) {
    val colors = AstridTheme.colors
    val userId = member.who ?: return
    val person = member.user ?: UserSummary(userId, member.name, member.email)
    val isAdmin = member.role.equals("admin", ignoreCase = true)
    PersonRow(person, onOpen = { onOpenProfile(userId) }) {
        if (!canManage) {
            Text(roleLabel(member.role), style = TextStyles.caption, color = colors.textMuted)
        } else {
            var open by remember { mutableStateOf(false) }
            val menuLabel = stringResource(R.string.lists_member_options)
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClickLabel = menuLabel) { open = true }
                        .semantics(mergeDescendants = true) { contentDescription = menuLabel }
                        .padding(horizontal = Spacing.s8, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(roleLabel(member.role), style = TextStyles.caption, color = colors.textMuted)
                    Icon(Icons.Filled.MoreVert, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isAdmin) R.string.lists_make_member else R.string.lists_make_admin)) },
                        leadingIcon = { Icon(if (isAdmin) Icons.Outlined.Person else Icons.Outlined.StarBorder, null) },
                        onClick = { open = false; vm.setRole(userId, if (isAdmin) "member" else "admin") },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lists_remove_member), color = Error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Error) },
                        onClick = { open = false; vm.remove(userId) },
                    )
                }
            }
        }
    }
}

/** iOS's Add / Remove pill: caption text on a 10% tint, radius 6. */
@Composable
private fun Pill(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label, style = TextStyles.caption, color = tint,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.s12, vertical = 6.dp),
    )
}
