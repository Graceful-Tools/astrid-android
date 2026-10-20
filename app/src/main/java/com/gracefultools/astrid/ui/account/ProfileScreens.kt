package com.gracefultools.astrid.ui.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.AccountUser
import com.gracefultools.astrid.model.Passkey
import com.gracefultools.astrid.ui.common.AppErrorBanner
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.settings.ButtonRow
import com.gracefultools.astrid.ui.settings.FormDivider
import com.gracefultools.astrid.ui.settings.FormRowFrame
import com.gracefultools.astrid.ui.settings.FormSection
import com.gracefultools.astrid.ui.settings.NavRow
import com.gracefultools.astrid.ui.settings.RowIcon
import com.gracefultools.astrid.ui.settings.SettingsPage
import com.gracefultools.astrid.ui.settings.ValueRow
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** iOS's system tints on the stat tiles. */
private val StatGreen = Color(0xFF34C759)
private val StatYellow = Color(0xFFFFCC00)
private val StatBlue = Color(0xFF007AFF)

/** A card the way iOS's profile draws one: bgSecondary, radius 8, a 1pt border. */
@Composable
private fun ProfileCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    val shape = RoundedCornerShape(Radius.medium)
    Box(modifier.fillMaxWidth().clip(shape).background(colors.bgSecondary).border(1.dp, colors.border, shape).padding(Spacing.s16)) { content() }
}

private fun localDate(iso: String?): java.time.ZonedDateTime? =
    iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() }

// ── Profile ──────────────────────────────────────────────────────────────────────────────────

/**
 * iOS's UserProfileView for the signed-in account: the user card (tap the photo or the name to edit),
 * then the three stat cards. Export lives on Account & Access now, as on iOS.
 */
@Composable
fun ProfileScreen(vm: ProfileViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadProfile() }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            FloatingTextHeader(stringResource(R.string.profile_title), icon = Icons.Outlined.AccountCircle, onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s16),
                verticalArrangement = Arrangement.spacedBy(Spacing.s16),
            ) {
                state.user?.let { UserCard(it, onEdit = { editing = true }) }
                val stats = state.stats
                if (stats != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                        StatCard(Icons.Filled.CheckCircle, StatGreen, stats.completed, R.string.stats_completed, R.string.stats_completed_tagline)
                        StatCard(Icons.Filled.Lightbulb, StatYellow, stats.inspired, R.string.stats_inspired, R.string.stats_inspired_tagline)
                        StatCard(Icons.Filled.Favorite, StatBlue, stats.supported, R.string.stats_supported, R.string.stats_supported_tagline)
                    }
                } else if (state.loadingStats) {
                    Column(Modifier.fillMaxWidth().padding(top = Spacing.s32), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                        CircularProgressIndicator(color = colors.accent)
                        Text(stringResource(R.string.profile_loading), style = TextStyles.body, color = colors.textMuted)
                    }
                }
            }
        }
        state.error?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s24)) { AppErrorBanner(it, vm::dismissError) } }
    }
    if (editing) state.user?.let { user -> EditProfileSheet(user, state.saving, vm, onDismiss = { editing = false }) }
}

/** iOS's userInfoCard: 80 avatar with a camera badge, 24 bold name, AI badge, email, "Joined". */
@Composable
private fun UserCard(user: AccountUser, onEdit: () -> Unit) {
    val colors = AstridTheme.colors
    val editLabel = stringResource(R.string.profile_edit_title)
    ProfileCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s16), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEdit)
                    .semantics { contentDescription = editLabel },
            ) {
                Avatar(user.image, initialsOf(user.name, user.email), 80.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp).size(24.dp).clip(CircleShape).background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.display, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false).clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEdit),
                    )
                    if (user.isAIAgent == true) {
                        Text(
                            stringResource(R.string.profile_ai_agent), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.small)).background(colors.accent).padding(horizontal = Spacing.s8, vertical = Spacing.s4),
                        )
                    }
                }
                user.email?.let { Text(it, style = TextStyles.footnote, color = colors.textMuted) }
                localDate(user.createdAt)?.let { joined ->
                    val month = joined.format(DateTimeFormatter.ofPattern("MMMM yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.profile_joined, month), style = TextStyles.footnote, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

/** iOS's statCard: a 48 tinted tile, then "13 Completed" at 24 bold over the tagline. */
@Composable
private fun StatCard(icon: ImageVector, tint: Color, value: Int, label: Int, tagline: Int) {
    val colors = AstridTheme.colors
    ProfileCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.medium)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text("$value ${stringResource(label)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(stringResource(tagline), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)
            }
        }
    }
}

/** A picked photo copied where the core can read it; the core uploads it. */
private suspend fun copyPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val type = context.contentResolver.getType(uri)
        val ext = when (type) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
        val file = java.io.File(context.cacheDir, "profile-photo-${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    }.getOrNull()
}

/** The system photo picker; answers a path the core can upload. */
@Composable
private fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { copyPhoto(context, uri)?.let(onPicked) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** iOS's EditProfileView, as the sheet it is there: a 120 photo with a camera button, name, Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(user: AccountUser, saving: Boolean, vm: ProfileViewModel, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    var name by remember { mutableStateOf(user.name.orEmpty()) }
    var photo by remember { mutableStateOf<String?>(null) }
    var savedOnce by remember { mutableStateOf(false) }
    val pick = rememberPhotoPicker { photo = it }
    val hasChanges = (name.trim().isNotEmpty() && name.trim() != user.name) || photo != null
    // Saving finished: iOS shows "Success!" then closes; the sheet closing says the same here.
    LaunchedEffect(saving) { if (savedOnce && !saving) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bgPrimary,
    ) {
        FloatingTextHeader(stringResource(R.string.profile_edit_title), icon = Icons.Outlined.AccountCircle, onBack = onDismiss)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.s16),
            verticalArrangement = Arrangement.spacedBy(Spacing.s24), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                Box(Modifier.size(120.dp)) {
                    if (photo != null) {
                        AsyncImage(model = java.io.File(photo!!), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(120.dp).clip(CircleShape))
                    } else {
                        Avatar(user.image, initialsOf(user.name, user.email), 120.dp)
                    }
                    IconButton(
                        onClick = pick,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp).size(36.dp).clip(CircleShape).background(colors.accent),
                    ) { Icon(Icons.Filled.PhotoCamera, stringResource(R.string.account_change_photo), tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                Text(stringResource(R.string.profile_change_photo), style = TextStyles.footnote, color = colors.textMuted)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.profile_display_name), style = TextStyles.footnote, color = colors.textSecondary)
                ProfileField(name, stringResource(R.string.profile_enter_name)) { name = it }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(stringResource(R.string.profile_email_address), style = TextStyles.footnote, color = colors.textSecondary)
                // The core changes name and photo only; the address is shown, not edited (see the report).
                ProfileField(user.email.orEmpty(), stringResource(R.string.profile_email_address), enabled = false) {}
            }
            val shape = RoundedCornerShape(Radius.medium)
            val label = stringResource(if (saving) R.string.profile_saving else R.string.profile_save_changes)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(if (hasChanges) colors.accent else colors.accent.copy(alpha = 0.5f))
                    .clickable(enabled = hasChanges && !saving, role = Role.Button, onClickLabel = label) {
                        savedOnce = true
                        vm.saveProfile(name, photo)
                    }
                    .padding(Spacing.s16),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.s8))
                }
                Text(label, style = TextStyles.body, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileField(value: String, placeholder: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    val colors = AstridTheme.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
        placeholder = { Text(placeholder, color = colors.inputPlaceholder) },
        textStyle = TextStyles.body.copy(color = colors.textPrimary),
        shape = RoundedCornerShape(Radius.medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.bgTertiary, unfocusedContainerColor = colors.bgTertiary, disabledContainerColor = colors.bgTertiary,
            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, disabledBorderColor = colors.border,
            disabledTextColor = colors.textSecondary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Account & Access ─────────────────────────────────────────────────────────────────────────

/** Where a passkey is added: the web's account page (registering one is the browser's, per the core). */
private val WEB_ACCOUNT_SETTINGS: String get() = com.gracefultools.astrid.Brand.webUrl("/settings/account")

/**
 * iOS's AccountSettingsView: profile (photo, name), email verification, account information,
 * passkeys, your data, and the danger zone.
 */
@Composable
fun AccountAccessScreen(vm: ProfileViewModel, onBack: () -> Unit, onDelete: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadUser(); vm.loadPasskeys() }
    // Back from the browser after adding a passkey: the list catches up.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        var paused = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) paused = true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && paused) { paused = false; vm.loadPasskeys() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.exported) {
        val path = state.exported ?: return@LaunchedEffect
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(path))
        val send = Intent(Intent.ACTION_SEND).setType(if (path.endsWith(".csv")) "text/csv" else "application/json")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
        vm.exportShared()
    }
    val user = state.user
    var name by remember(user?.name) { mutableStateOf(user?.name.orEmpty()) }
    var photo by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhotoPicker { photo = it }
    LaunchedEffect(state.saving) { if (!state.saving) photo = null }
    val isOAuth = user?.verifiedViaOAuth == true

    SettingsPage(
        stringResource(R.string.settings_account_access), Icons.Outlined.AccountCircle, onBack,
        overlay = { state.error?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s24)) { AppErrorBanner(it, vm::dismissError) } } },
    ) {
        if (user == null) {
            Text(stringResource(R.string.account_loading), style = TextStyles.body, color = colors.textMuted, modifier = Modifier.padding(Spacing.s16))
            return@SettingsPage
        }

        FormSection(stringResource(R.string.profile_title), if (isOAuth) stringResource(R.string.account_oauth_footer) else null) {
            FormRowFrame {
                Box(Modifier.size(60.dp).clip(CircleShape).border(2.dp, colors.accent, CircleShape)) {
                    if (photo != null) {
                        AsyncImage(model = java.io.File(photo!!), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(60.dp))
                    } else {
                        Avatar(user.image, initialsOf(user.name, user.email), 60.dp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                    Text(user.display, style = TextStyles.headline, color = colors.textPrimary)
                    user.email?.let { Text(it, style = TextStyles.footnote, color = colors.textSecondary) }
                }
                IconButton(onClick = pick, enabled = !state.saving) {
                    if (state.saving && photo != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.PhotoCamera, stringResource(R.string.account_change_photo), tint = colors.accent)
                }
            }
            FormDivider(inset = false)
            FormRowFrame {
                RowIcon(Icons.Filled.Person)
                Text(stringResource(R.string.account_display_name), style = TextStyles.body, color = colors.textPrimary)
                androidx.compose.foundation.text.BasicTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyles.body.copy(color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.saveProfile(name, photo) }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.weight(1f).semanticsLabel(stringResource(R.string.account_display_name)),
                )
            }
            FormDivider(inset = false)
            FormRowFrame {
                RowIcon(Icons.Filled.Email)
                Text(stringResource(R.string.account_email), style = TextStyles.body, color = colors.textPrimary)
                // Read-only: the core's updateProfile takes a name and a photo, not an address.
                Text(user.email.orEmpty(), style = TextStyles.body, color = colors.textSecondary, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
            }
            val hasChanges = (name.trim().isNotEmpty() && name.trim() != user.name) || photo != null
            if (hasChanges) {
                FormDivider(inset = false)
                ButtonRow(
                    stringResource(if (state.saving) R.string.messages_saving else R.string.account_save_changes),
                    enabled = !state.saving, centered = true,
                    trailing = { if (state.saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) },
                ) { vm.saveProfile(name, photo) }
            }
        }

        val pending = user.hasPendingChange == true && user.pendingEmail != null
        val unverified = user.verified == false && !isOAuth
        if (pending || unverified) {
            FormSection(
                stringResource(R.string.account_email_verification),
                stringResource(if (pending) R.string.account_pending_email_footer else R.string.account_verify_email_footer),
            ) {
                if (pending) {
                    FormRowFrame {
                        RowIcon(Icons.Filled.Email, Warning)
                        Column {
                            Text(stringResource(R.string.account_pending_email), style = TextStyles.body, color = Warning)
                            Text(user.pendingEmail.orEmpty(), style = TextStyles.footnote, color = colors.textSecondary)
                        }
                    }
                    FormDivider()
                    ButtonRow(stringResource(R.string.account_resend_verification), icon = Icons.Filled.Refresh, onClick = vm::resendVerification)
                } else {
                    FormRowFrame {
                        RowIcon(Icons.Filled.Warning, Warning)
                        Text(stringResource(R.string.account_email_not_verified), style = TextStyles.body, color = Warning)
                    }
                    FormDivider()
                    ButtonRow(stringResource(R.string.account_send_verification), icon = Icons.Filled.Email, onClick = vm::resendVerification)
                }
            }
        }

        FormSection(stringResource(R.string.account_info)) {
            ValueRow(stringResource(R.string.account_id), user.id.take(8) + "...", icon = Icons.Filled.Numbers)
            FormDivider()
            ValueRow(
                stringResource(R.string.account_created),
                localDate(user.createdAt)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "",
                icon = Icons.Filled.CalendarMonth,
            )
            FormDivider()
            ValueRow(
                stringResource(R.string.account_auth_method),
                stringResource(if (isOAuth) R.string.account_google else R.string.account_passkey),
                icon = Icons.Filled.Key,
            )
        }

        FormSection(stringResource(R.string.account_passkeys), stringResource(R.string.account_passkeys_footer)) {
            PasskeyRows(state.passkeys, state.loadingPasskeys, vm)
            FormDivider(inset = false)
            ButtonRow(stringResource(R.string.account_add_passkey_button), icon = Icons.Filled.AddCircle, color = PasskeyIndigo) {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(WEB_ACCOUNT_SETTINGS))
            }
        }

        FormSection(stringResource(R.string.account_your_data), stringResource(R.string.account_your_data_footer)) {
            listOf("json" to (R.string.account_export_json to Icons.Filled.IosShare), "csv" to (R.string.account_export_csv to Icons.Filled.TableChart))
                .forEachIndexed { index, (format, labelIcon) ->
                    if (index > 0) FormDivider()
                    val title = stringResource(labelIcon.first)
                    if (state.exporting) {
                        FormRowFrame {
                            RowIcon(labelIcon.second)
                            Text(title, style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        NavRow(title, labelIcon.second) {
                            val dir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
                            vm.export(format, java.io.File(dir, "astrid-export.$format").absolutePath)
                        }
                    }
                }
        }

        FormSection(stringResource(R.string.account_danger_zone), stringResource(R.string.account_delete_account_footer)) {
            ButtonRow(stringResource(R.string.account_delete_account), icon = Icons.Filled.Delete, color = Error, onClick = onDelete)
        }
    }

    state.success?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissSuccess,
            title = { Text(stringResource(R.string.messages_success)) },
            text = { if (message.isNotBlank()) Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissSuccess) { Text(stringResource(R.string.actions_ok)) } },
        )
    }
}

/** iOS's purple-indigo "Add a Passkey". */
private val PasskeyIndigo = Color(0xFF5957D6)

/** A label for a field that has no visible one of its own. */
private fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }

/** iOS's passkey rows: name, added date (· Synced), rename inline, delete. */
@Composable
private fun PasskeyRows(passkeys: List<Passkey>?, loading: Boolean, vm: ProfileViewModel) {
    val colors = AstridTheme.colors
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    val fallbackName = stringResource(R.string.account_passkey)
    when {
        passkeys == null && loading -> FormRowFrame {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.account_loading_passkeys), style = TextStyles.footnote, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
        }
        passkeys.isNullOrEmpty() -> Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.s12, horizontal = Spacing.s16),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Icon(Icons.Filled.Password, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.account_no_passkeys_registered), style = TextStyles.body, color = colors.textMuted)
            Text(stringResource(R.string.account_add_passkey_description), style = TextStyles.footnote, color = colors.textMuted)
        }
        else -> passkeys.forEachIndexed { index, key ->
            if (index > 0) FormDivider()
            FormRowFrame {
                RowIcon(Icons.Filled.Key, colors.textMuted)
                Column(Modifier.weight(1f)) {
                    if (editingId == key.id) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = editingName, onValueChange = { editingName = it }, singleLine = true,
                            textStyle = TextStyles.body.copy(color = colors.textPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.renamePasskey(key.id, editingName); editingId = null }),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.account_passkey_name_placeholder)),
                        )
                    } else {
                        Text(key.name ?: fallbackName, style = TextStyles.body, color = colors.textPrimary)
                    }
                    val added = localDate(key.createdAt)?.let { stringResource(R.string.account_added_on, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                        ?: stringResource(R.string.account_added_recently)
                    Text(added + if (key.isSynced) " • " + stringResource(R.string.account_synced) else "", style = TextStyles.footnote, color = colors.textMuted)
                }
                if (editingId == key.id) {
                    IconButton(onClick = { vm.renamePasskey(key.id, editingName); editingId = null }) {
                        Icon(Icons.Filled.CheckCircle, stringResource(R.string.account_save_passkey_name), tint = Success)
                    }
                    IconButton(onClick = { editingId = null; editingName = "" }) {
                        Icon(Icons.Filled.Cancel, stringResource(R.string.common_cancel), tint = colors.textMuted)
                    }
                } else {
                    IconButton(onClick = { editingId = key.id; editingName = key.name ?: fallbackName }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.account_edit_passkey), tint = colors.textMuted)
                    }
                    IconButton(onClick = { vm.revokePasskey(key.id) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.actions_delete), tint = Error)
                    }
                }
            }
        }
    }
}

// ── Delete account ───────────────────────────────────────────────────────────────────────────

/**
 * iOS's DeleteAccountView: what goes, the type-to-confirm phrase with live match feedback, then a
 * final "Delete Forever" alert. The core deletes and signs this device out; [onSignedOut] follows.
 */
@Composable
fun DeleteAccountScreen(vm: ProfileViewModel, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.resetDelete() } }

    SettingsPage(
        stringResource(R.string.account_delete_account), Icons.Outlined.Delete, onBack,
        overlay = { state.error?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s24)) { AppErrorBanner(it, vm::dismissError) } } },
    ) {
        FormSection {
            Column(Modifier.fillMaxWidth().padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Error, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.account_delete_warning), style = TextStyles.headline, color = Error)
                }
                Text(stringResource(R.string.account_delete_remove_list_header), style = TextStyles.body, color = colors.textPrimary)
                Column(Modifier.padding(start = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    listOf(
                        R.string.account_delete_remove_tasks, R.string.account_delete_remove_lists, R.string.account_delete_remove_comments,
                        R.string.account_delete_remove_profile, R.string.account_delete_remove_files,
                    ).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                            Text("•", color = Error, style = TextStyles.footnote)
                            Text(stringResource(line), style = TextStyles.footnote, color = colors.textSecondary)
                        }
                    }
                }
            }
        }

        val footer: @Composable () -> Unit = {
            when {
                state.deleteText.isEmpty() -> Text(stringResource(R.string.account_delete_confirm_instruction), style = TextStyles.footnote, color = colors.textSecondary)
                !state.deleteMatches -> FeedbackLine(Icons.Outlined.Cancel, stringResource(R.string.account_delete_confirm_mismatch), Error)
                else -> FeedbackLine(Icons.Outlined.CheckCircle, stringResource(R.string.account_delete_confirm_match), Success)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            FormSection(stringResource(R.string.account_delete_confirmation)) {
                Column(Modifier.fillMaxWidth().padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    Text(stringResource(R.string.account_delete_confirm_prompt), style = TextStyles.footnote, color = colors.textSecondary)
                    OutlinedTextField(
                        value = state.deleteText, onValueChange = vm::setDeleteText, singleLine = true,
                        placeholder = { Text(stringResource(R.string.account_delete_confirm_placeholder), color = colors.inputPlaceholder) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        textStyle = TextStyles.body.copy(color = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(Modifier.padding(horizontal = Spacing.s16)) { footer() }
        }

        FormSection {
            ButtonRow(
                stringResource(if (state.deleting) R.string.messages_deleting else R.string.account_delete_my_account_button),
                icon = if (state.deleting) null else Icons.Filled.Delete, color = Error, centered = true,
                enabled = state.deleteMatches && !state.deleting,
                trailing = { if (state.deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Error) },
                onClick = vm::askDelete,
            )
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.account_delete_final_confirmation)) },
            text = { Text(stringResource(R.string.account_delete_final_warning)) },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.common_cancel)) } },
            confirmButton = {
                TextButton(onClick = vm::deleteAccount) { Text(stringResource(R.string.account_delete_forever), color = Error, fontWeight = FontWeight.SemiBold) }
            },
        )
    }
}

@Composable
private fun FeedbackLine(icon: ImageVector, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = TextStyles.footnote, color = tint)
    }
}
