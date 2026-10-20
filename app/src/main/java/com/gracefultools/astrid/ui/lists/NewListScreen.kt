package com.gracefultools.astrid.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS ListEditView for a new list ("New list"): the name, a description and the people to invite,
 * with Cancel and Create. Android pushes it as a screen (back = Cancel) where iOS presents a sheet,
 * and opens the new list once it is made (iOS just dismisses).
 */
@Composable
fun NewListScreen(vm: NewListViewModel, onCancel: () -> Unit, onCreated: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    var adding by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)
    LaunchedEffect(state.done) { if (state.done) state.createdId?.let(onCreated) }

    Column(Modifier.fillMaxSize().background(colors.pageBg).imePadding()) {
        FloatingTextHeader(stringResource(R.string.lists_new_list), onBack = onCancel) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = vm::create, enabled = state.canCreate) {
                    Text(stringResource(R.string.lists_create), style = TextStyles.headline)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Spacing.s24),
            verticalArrangement = Arrangement.spacedBy(Spacing.s24),
        ) {
            // 1. List name
            Column(Modifier.padding(top = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.lists_list_name), style = TextStyles.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.s16))
                val placeholder = stringResource(R.string.lists_enter_name)
                Box(
                    Modifier.padding(horizontal = Spacing.s16).fillMaxWidth().clip(RoundedCornerShape(Radius.medium))
                        .background(colors.bgSecondary).padding(Spacing.s12),
                ) {
                    val style = TextStyles.body.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    if (state.name.isEmpty()) Text(placeholder, style = style, color = colors.inputPlaceholder)
                    BasicTextField(
                        state.name, vm::setName, singleLine = true, textStyle = style.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                    )
                }
            }
            HorizontalDivider(color = colors.border)

            // 2. Description
            Column(Modifier.padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.lists_list_description), style = TextStyles.caption, color = colors.textSecondary)
                val placeholder = stringResource(R.string.lists_description_placeholder)
                Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).padding(Spacing.s12)) {
                    if (state.description.isEmpty()) Text(placeholder, style = TextStyles.body, color = colors.inputPlaceholder)
                    BasicTextField(
                        state.description, vm::setDescription, textStyle = TextStyles.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                    )
                }
            }
            HorizontalDivider(color = colors.border)

            // 3. Members
            Column(Modifier.padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lists_members), style = TextStyles.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.small)).clickable(role = Role.Button) { adding = true }
                            .heightIn(min = 32.dp).padding(horizontal = Spacing.s4),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
                    ) {
                        Icon(Icons.Outlined.PersonAdd, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.actions_add), style = TextStyles.caption, color = colors.accent)
                    }
                }
                if (state.invitees.isEmpty()) {
                    Text(stringResource(R.string.lists_no_members), style = TextStyles.caption2, color = colors.textMuted)
                } else state.invitees.forEach { invitee ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).padding(Spacing.s12),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text(invitee.email.take(1).uppercase(), style = TextStyles.caption, color = colors.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(invitee.email, style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                            Text(
                                stringResource(if (invitee.role == "admin") R.string.lists_role_admin else R.string.lists_role_member),
                                style = TextStyles.caption2, color = colors.textSecondary,
                            )
                        }
                        IconButton(onClick = { vm.removeInvitee(invitee.email) }) {
                            Icon(Icons.Filled.Cancel, stringResource(R.string.lists_remove_invitee, invitee.email), tint = Color.Gray)
                        }
                    }
                }
            }

            // Errors: a refused create, or the people a made list could not take.
            val message = state.error ?: state.failedEmails.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.lists_created_failed_to_add, it.joinToString(", "))
            }
            message?.let { ErrorBox(it, Modifier.padding(horizontal = Spacing.s16)) }
        }
    }

    if (adding) {
        AddMemberSheet(
            state.suggestions, searching = false, onQuery = vm::search,
            onAdd = { email, role -> vm.addInvitee(email, role); vm.clearSuggestions(); adding = false },
            onDismiss = { vm.clearSuggestions(); adding = false },
        )
    }
}
