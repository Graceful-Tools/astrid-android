package com.gracefultools.astrid.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListSettingsScreen(vm: ListSettingsViewModel, onBack: () -> Unit, onGone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.gone) { if (state.gone) onGone() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    val s = state.settings
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back), tint = colors.textPrimary) }
                Text(stringResource(R.string.lists_settings), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
            if (s != null) Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                Group {
                    var name by rememberSaveable(s.name) { mutableStateOf(s.name) }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.lists_name)) },
                        enabled = s.canManageList, singleLine = true,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.rename(name) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.lists_favorite), color = colors.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Switch(checked = s.isFavorite, onCheckedChange = vm::setFavorite)
                    }
                }
                if (s.canManageList) {
                    Label(stringResource(R.string.lists_color))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.colorChoices.forEach { choice ->
                            val chosen = choice.equals(s.color, ignoreCase = true)
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(parseColor(choice))
                                    .then(if (chosen) Modifier.border(3.dp, colors.textPrimary, CircleShape) else Modifier)
                                    .clickable { vm.setColor(choice) }
                                    .semantics { selected = chosen; contentDescription = choice },
                            )
                        }
                    }
                    Label(stringResource(R.string.lists_privacy))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PRIVATE" to R.string.lists_privacy_private, "SHARED" to R.string.lists_privacy_shared, "PUBLIC" to R.string.lists_privacy_public).forEach { (value, label) ->
                            val chosen = s.privacy.equals(value, ignoreCase = true)
                            Text(
                                stringResource(label), color = if (chosen) colors.accentText else colors.textPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (chosen) colors.accent else colors.bgTertiary)
                                    .clickable { vm.setPrivacy(value) }.semantics { selected = chosen }.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Label(stringResource(R.string.lists_members))
                Group {
                    s.members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.display, color = colors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(if (member.role.equals("admin", true)) R.string.lists_role_admin else R.string.lists_role_member),
                                color = colors.textSecondary, fontSize = 14.sp,
                                modifier = Modifier.then(
                                    if (s.canManageMembers && member.who != null) Modifier.clickable {
                                        vm.setRole(member.who!!, if (member.role.equals("admin", true)) "member" else "admin")
                                    } else Modifier,
                                ).padding(8.dp),
                            )
                            if (s.canManageMembers && member.who != null && member.who != s.currentUserId) {
                                TextButton(onClick = { vm.remove(member.who!!) }) { Text(stringResource(R.string.lists_remove_member), color = Error) }
                            }
                        }
                    }
                    if (s.canManageMembers) {
                        var email by rememberSaveable { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.lists_invite_email)) }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.invite(email); email = "" }) { Text(stringResource(R.string.lists_invite)) }
                        }
                    }
                }
                if (s.canLeave) DangerRow(stringResource(R.string.lists_leave), vm::leave)
                if (s.canDeleteList) DangerRow(stringResource(R.string.lists_delete)) { confirmDelete = true }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.lists_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.lists_delete), color = Error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AstridTheme.colors.textMuted)

@Composable
private fun Group(content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (colors.isDark) colors.bgSecondary else Color.White)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp)).padding(Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) { content() }
}

@Composable
private fun DangerRow(text: String, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Text(
        text, color = Error, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (colors.isDark) colors.bgSecondary else Color.White)
            .clickable(onClick = onClick).padding(Spacing.s16),
    )
}
