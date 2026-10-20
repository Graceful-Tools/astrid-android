package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.detail.PickRow
import com.gracefultools.astrid.ui.detail.PickerSheet
import com.gracefultools.astrid.ui.tasks.parseColor
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.PriorityColors
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS ListColorSwatches: the palette as 28dp circles spread evenly across the row, a white
 * checkmark on the chosen one, then a custom colour well (Android has no system colour picker, so
 * the well opens [CustomColorSheet]).
 */
@Composable
internal fun ColorSwatches(choices: List<String>, current: String, onPick: (String) -> Unit, onCustom: () -> Unit) {
    val isCustom = choices.none { it.equals(current, ignoreCase = true) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        choices.forEach { swatch ->
            val chosen = swatch.equals(current, ignoreCase = true)
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .clickable(role = Role.RadioButton) { onPick(swatch) }
                    .semantics(mergeDescendants = true) { selected = chosen; contentDescription = swatch },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(parseColor(swatch)), contentAlignment = Alignment.Center) {
                    if (chosen) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        val customLabel = stringResource(R.string.lists_custom_color)
        Box(
            Modifier.weight(1f).heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onCustom)
                .semantics(mergeDescendants = true) { selected = isCustom; contentDescription = customLabel },
            contentAlignment = Alignment.Center,
        ) {
            val wheel = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
            Box(Modifier.size(28.dp).clip(CircleShape).background(wheel).padding(4.dp).clip(CircleShape).background(parseColor(current)), contentAlignment = Alignment.Center) {
                if (isCustom) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** A custom colour: hue, saturation and brightness sliders over a live swatch. Each move is reported. */
@Composable
internal fun CustomColorSheet(initial: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(parseColor(initial).toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.2f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f)) }
    fun hex(): String = String.format("#%06x", android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)) and 0xFFFFFF)
    PickerSheet(stringResource(R.string.lists_custom_color), onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radius.large)).background(parseColor(hex())))
        val hueLabel = stringResource(R.string.lists_color_hue)
        val saturationLabel = stringResource(R.string.lists_color_saturation)
        val brightnessLabel = stringResource(R.string.lists_color_brightness)
        val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
        Slider(hue, { hue = it; onChange(hex()) }, valueRange = 0f..359f, colors = sliderColors, modifier = Modifier.semantics { contentDescription = hueLabel })
        Slider(saturation, { saturation = it; onChange(hex()) }, valueRange = 0f..1f, colors = sliderColors, modifier = Modifier.semantics { contentDescription = saturationLabel })
        Slider(value, { value = it; onChange(hex()) }, valueRange = 0f..1f, colors = sliderColors, modifier = Modifier.semantics { contentDescription = brightnessLabel })
    }
}

/** iOS's PriorityButtonPicker: ○ ! !! !!!, 40 x 40, radius 6, 12 apart, filled when chosen. */
@Composable
internal fun PriorityButtons(current: Int, onPick: (Int) -> Unit) {
    val names = listOf(R.string.priority_none, R.string.priority_low, R.string.priority_medium, R.string.priority_high)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        for (level in 0..3) {
            val tint = PriorityColors[level]
            val chosen = level == current
            val spoken = stringResource(R.string.priority_a11y, stringResource(names[level]))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radius.small))
                    .background(if (chosen) tint else Color.Transparent)
                    .border(1.5.dp, tint, RoundedCornerShape(Radius.small))
                    .clickable(role = Role.RadioButton) { onPick(level) }
                    .semantics(mergeDescendants = true) { selected = chosen; contentDescription = spoken },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (level == 0) "○" else "!".repeat(level), color = if (chosen) Color.White else tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/**
 * iOS AddMemberSheet: an email field whose suggestions come from the server's people search, a
 * Member / Admin role picker, and Cancel / Add. The contacts-upload prompt is iOS's Contacts
 * framework and has no Android counterpart here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddMemberSheet(
    suggestions: List<UserSummary>,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onAdd: (email: String, role: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AstridTheme.colors
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("member") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bgPrimary,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Text(stringResource(R.string.members_add_member), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { onAdd(email.trim(), role) }, enabled = email.contains('@')) { Text(stringResource(R.string.actions_add), fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.s24), verticalArrangement = Arrangement.spacedBy(Spacing.s24)) {
            FormSection(stringResource(R.string.members_email_address)) {
                val emailLabel = stringResource(R.string.members_email_address)
                FormRow {
                    BasicTextField(
                        email, { email = it; onQuery(it) }, singleLine = true,
                        textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = emailLabel },
                    )
                }
                if (email.length >= 2) suggestions.forEach { person ->
                    FormDivider()
                    FormRow(onClick = { email = person.email.orEmpty(); onQuery("") }) {
                        Avatar(person.image, initialsOf(person.name, person.email), 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(person.display, style = TextStyles.body, color = colors.textPrimary, maxLines = 1)
                            person.email?.let { Text(it, style = TextStyles.caption2, color = colors.textSecondary, maxLines = 1) }
                        }
                    }
                }
                if (searching) {
                    FormDivider()
                    FormRow {
                        CircularProgressIndicator(Modifier.size(16.dp), color = colors.accent, strokeWidth = 2.dp)
                        Text(stringResource(R.string.members_searching), style = TextStyles.caption2, color = colors.textMuted)
                    }
                }
            }
            FormSection(stringResource(R.string.members_role)) {
                Box(Modifier.padding(Spacing.s8)) {
                    SegmentedTabs(
                        listOf(null to stringResource(R.string.lists_role_member), null to stringResource(R.string.lists_role_admin)),
                        selected = if (role == "admin") 1 else 0,
                        onSelect = { role = if (it == 1) "admin" else "member" },
                    )
                }
            }
        }
    }
}

/**
 * iOS ShareTargetView for a list: the privacy notice, the minted link (core `shareList`), Copy to
 * Clipboard and the system share sheet; "Failed to generate share link" when minting fails.
 */
@Composable
internal fun ShareListSheet(name: String, privacy: String?, link: String?, failed: Boolean, onDismiss: () -> Unit) {
    val colors = AstridTheme.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    PickerSheet(stringResource(R.string.lists_share_list), onDismiss) {
        val note = if (privacy.equals("PUBLIC", ignoreCase = true)) R.string.share_public_list_message else R.string.share_private_list_message
        Text(stringResource(note), style = TextStyles.subheadline, color = colors.textSecondary)
        when {
            failed -> Text(stringResource(R.string.share_link_failed), style = TextStyles.body, color = Error)
            link == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
                Text(stringResource(R.string.share_generating_link), style = TextStyles.body, color = colors.textSecondary)
            }
            else -> {
                Text(link, style = TextStyles.subheadline, color = colors.textPrimary, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(colors.bgSecondary).padding(Spacing.s12))
                Text(stringResource(R.string.share_share_list_link_hint), style = TextStyles.caption, color = colors.textMuted)
                PickRow(stringResource(if (copied) R.string.share_link_copied else R.string.share_copy_link), copied, leading = { Icon(Icons.Outlined.ContentCopy, null, tint = colors.accent) }) {
                    clipboard.setText(AnnotatedString(link)); copied = true
                }
                PickRow(stringResource(R.string.actions_share), false, leading = { Icon(Icons.Outlined.Share, null, tint = colors.accent) }) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, name).putExtra(android.content.Intent.EXTRA_TEXT, link)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }
            }
        }
    }
}

/** iOS's inline error box: red caption text on a 10% red rounded card. */
@Composable
internal fun ErrorBox(message: String, modifier: Modifier = Modifier) {
    Text(
        message, style = TextStyles.caption, color = Error,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.medium)).background(Error.copy(alpha = 0.1f)).padding(Spacing.s12),
    )
}
