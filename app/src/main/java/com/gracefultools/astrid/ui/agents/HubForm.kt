package com.gracefultools.astrid.ui.agents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.common.FloatingTextHeader
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.Warning
import kotlinx.coroutines.delay

/**
 * The server this build talks to — the core's default (`CoreSession`'s `baseUrl` is not overridden on
 * Android). iOS's `AgentHubLinks` builds its web links from `Constants.API.baseURL` the same way.
 */
internal const val WEB_ORIGIN = "https://astrid.cc"

/** iOS opens these in-app; Android's equivalent is a Custom Tab (ASTRID.md §2). */
internal fun openInCustomTab(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
}

internal fun copyToClipboard(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, text))
}

/**
 * A hub sub-screen, iOS `agentHubScreenChrome`: the floating text header over the themed background,
 * then the form's sections scrolling under it.
 */
@Composable
internal fun HubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        FloatingTextHeader(title, onBack = onBack, actions = actions)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s16).padding(top = Spacing.s16, bottom = Spacing.s32)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s20),
            content = content,
        )
    }
}

/** One inset-grouped Form section: an optional header above, the rows on a card, an optional footer below. */
@Composable
internal fun FormSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AstridTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        when {
            headerContent != null -> Box(Modifier.padding(horizontal = Spacing.s16).semantics { heading() }) { headerContent() }
            header != null -> Text(
                header.uppercase(), style = TextStyles.footnote, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.s16).semantics { heading() },
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(formCard())
                .padding(horizontal = Spacing.s16, vertical = Spacing.s12),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
            content = content,
        )
        if (footer != null || footerContent != null) {
            Column(Modifier.padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                if (footer != null) Text(footer, style = TextStyles.footnote, color = colors.textSecondary)
                footerContent?.invoke(this)
            }
        }
    }
}

@Composable
internal fun formCard(): Color = AstridTheme.colors.let { if (it.isDark) it.bgSecondary else Color.White }

@Composable
internal fun FormDivider() = HorizontalDivider(color = AstridTheme.colors.divider, thickness = 0.5.dp)

@Composable
internal fun Caption(text: String, color: Color = AstridTheme.colors.textSecondary, modifier: Modifier = Modifier) =
    Text(text, style = TextStyles.caption, color = color, modifier = modifier)

@Composable
internal fun Caption2(text: String, color: Color = AstridTheme.colors.textSecondary, modifier: Modifier = Modifier) =
    Text(text, style = TextStyles.caption2, color = color, modifier = modifier)

/** iOS `Label(text, systemImage: "exclamationmark.triangle.fill")` in red, or orange for a setup warning. */
@Composable
internal fun ProblemLabel(text: String, color: Color = Error) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = TextStyles.caption, color = color)
    }
}

@Composable
internal fun LoadingRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AstridTheme.colors.textSecondary)
        Text(text, style = TextStyles.body, color = AstridTheme.colors.textPrimary)
    }
}

@Composable
internal fun SmallSpinner() = CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AstridTheme.colors.textSecondary)

/** A Form button row: accent label with its icon, red when destructive, greyed while disabled. */
@Composable
internal fun ActionRow(
    label: String,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AstridTheme.colors
    val tint = when {
        !enabled -> colors.textMuted
        destructive -> Error
        else -> colors.accent
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (small) 32.dp else 44.dp).clip(RoundedCornerShape(Radius.small))
            .clickable(enabled = enabled && !busy, onClick = onClick, role = Role.Button),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(if (small) 14.dp else 18.dp))
        Text(label, style = if (small) TextStyles.caption2 else TextStyles.body, color = tint, modifier = Modifier.weight(1f))
        if (busy) SmallSpinner()
    }
}

/** A small borderless text button, as iOS's `.buttonStyle(.borderless)` rows of Test / Update / Delete. */
@Composable
internal fun InlineButton(label: String, color: Color = AstridTheme.colors.accent, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, style = TextStyles.caption, color = if (enabled) color else AstridTheme.colors.textMuted,
        modifier = Modifier.clip(RoundedCornerShape(Radius.small)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 32.dp).padding(horizontal = Spacing.s8, vertical = Spacing.s8),
    )
}

/** iOS `Toggle` in a Form: the label, then the switch; the whole row toggles. */
@Composable
internal fun ToggleRow(label: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.body, color = colors.textPrimary)
            if (subtitle != null) Caption2(subtitle)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** iOS `NavigationLink` row: leading art, title and subtitle, a chevron. */
@Composable
internal fun NavRow(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Radius.small)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TextStyles.body, color = colors.textPrimary)
            if (subtitle != null) Caption2(subtitle)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

/**
 * iOS `.pickerStyle(.segmented)`: a grey track with the chosen segment raised on a white pill.
 * Each segment is a radio button to accessibility.
 */
@Composable
internal fun SegmentedControl(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(if (colors.isDark) colors.bgTertiary else Color(0xFFEEEEEF)).padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selected
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp)
                    .then(if (chosen) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(if (colors.isDark) colors.bgActive else Color.White) else Modifier)
                    .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = Spacing.s4, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = TextStyles.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (enabled) colors.textPrimary else colors.textMuted,
                )
            }
        }
    }
}

/** A labelled radio row, as a menu-style picker's choices are drawn in a sheet or inline. */
@Composable
internal fun RadioRow(label: String, selected: Boolean, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(Radius.small))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        leading?.invoke()
        Text(label, style = TextStyles.body, color = if (enabled) colors.textPrimary else colors.textMuted, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

/** iOS `CopyableCodeBlock`: monospaced, scrolls sideways, with Copy → Copied for two seconds. */
@Composable
internal fun CopyableCodeBlock(code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Text(
            code, style = TextStyles.caption, fontFamily = FontFamily.Monospace, color = AstridTheme.colors.textPrimary, softWrap = false,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        )
        Row(
            Modifier.clip(RoundedCornerShape(Radius.small)).clickable(role = Role.Button) { copyToClipboard(context, code); copied = true }.padding(Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, null, tint = AstridTheme.colors.accent, modifier = Modifier.size(12.dp))
            Caption2(stringResource(if (copied) R.string.settings_agents_copied else R.string.actions_copy), AstridTheme.colors.accent)
        }
    }
}

/** iOS `CredentialCopyRow`: a label, the value (orange when secret), and a copy button that ticks. */
@Composable
internal fun CredentialCopyRow(label: String, value: String, secret: Boolean = false) {
    val context = LocalContext.current
    val colors = AstridTheme.colors
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Caption2(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value, style = TextStyles.caption, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (secret) Warning else colors.textPrimary, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s8))
            Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, stringResource(R.string.actions_copy),
                tint = if (copied) Success else colors.accent,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { copyToClipboard(context, value); copied = true }.padding(Spacing.s8),
            )
        }
    }
}

/** iOS `WebSessionRequiredRow`: this change needs a signed-in web session, and a way there. */
@Composable
internal fun WebSessionRequiredRow() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Caption(stringResource(R.string.settings_agents_session_required))
        ActionRow(stringResource(R.string.settings_agents_open_web), Icons.Filled.Public, small = true) {
            openInCustomTab(context, "$WEB_ORIGIN/settings/agents")
        }
    }
}

/** A Form text field: a rounded field in the theme's field colours, a placeholder, optionally secret or monospaced. */
@Composable
internal fun FormTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    label: String? = null,
) {
    val colors = AstridTheme.colors
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = TextStyles.callout, color = colors.inputPlaceholder) },
        label = label?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyles.callout.copy(color = colors.textPrimary, fontFamily = if (monospace) FontFamily.Monospace else null),
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (secret) androidx.compose.ui.text.input.KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
        ),
        shape = RoundedCornerShape(Radius.medium),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.field, unfocusedContainerColor = colors.field,
            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.fieldBorder,
        ),
    )
}

/** The server's ISO 8601 stamp as the reader's medium date (and time), or null when it is not one. */
internal fun displayDate(iso: String?, withTime: Boolean = false): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(iso) }.getOrNull()
        ?: return null
    val formatter = if (withTime) {
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
    } else {
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    }
    return formatter.withZone(java.time.ZoneId.systemDefault()).format(instant)
}
