package com.gracefultools.astrid.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gracefultools.astrid.R
import com.gracefultools.astrid.core.CoreRules
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * iOS DescriptionReaderView (AITD-577, AITD-649 → AATD-102): the description given the whole
 * screen. Edit / Preview on the leading side when you can edit, Done on the trailing side; Done
 * (or Back) saves only a change, and clearing it counts as one.
 */
@Composable
fun DescriptionReaderScreen(
    text: String,
    blocks: List<JsonObject>,
    isReadOnly: Boolean,
    entry: DescriptionReader.Entry,
    onReference: (String, String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AstridTheme.colors
    var draft by rememberSaveable { mutableStateOf(text) }
    var mode by rememberSaveable { mutableStateOf(DescriptionReader.initialMode(text, isReadOnly, entry)) }
    val close = {
        if (!isReadOnly) DescriptionReader.textToSave(draft, text)?.let(onSave)
        onDismiss()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = close)
        Column(Modifier.fillMaxSize().background(colors.bgPrimary).safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s8)) {
                if (!isReadOnly) {
                    TextButton(
                        onClick = { mode = if (mode == DescriptionReader.Mode.READING) DescriptionReader.Mode.EDITING else DescriptionReader.Mode.READING },
                        modifier = Modifier.align(Alignment.CenterStart).testTag("descriptionReader.toggleMode"),
                    ) {
                        Text(
                            stringResource(if (mode == DescriptionReader.Mode.READING) R.string.actions_edit else R.string.task_detail_description_preview),
                            style = TextStyles.body, color = colors.accentInk,
                        )
                    }
                }
                Text(stringResource(R.string.detail_description), style = TextStyles.headline, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
                TextButton(onClick = close, modifier = Modifier.align(Alignment.CenterEnd).testTag("descriptionReader.done")) {
                    Text(stringResource(R.string.actions_done), style = TextStyles.body, fontWeight = FontWeight.SemiBold, color = colors.accentInk)
                }
            }
            HorizontalDivider(color = colors.divider)
            when (mode) {
                DescriptionReader.Mode.READING -> {
                    // The core renders what is on screen: the saved blocks, or the draft once it changed.
                    val shown = remember(draft) { if (draft == text) blocks else rendered(draft) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s16).testTag("descriptionReader.reading")) {
                        if (shown != null) SelectionContainer { MarkdownView(shown, onReference = onReference) }
                        else SelectionContainer { Text(draft, style = TextStyles.body, color = colors.textPrimary) }
                    }
                }
                DescriptionReader.Mode.EDITING -> {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        textStyle = TextStyles.body.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s16, vertical = Spacing.s12)
                            .focusRequester(focus).testTag("descriptionReader.editor"),
                    )
                }
            }
        }
    }
}

private fun rendered(markdown: String): List<JsonObject>? = runCatching {
    (CoreRules.ask(command("renderMarkdown", "text" to markdown)) as JsonArray).map { it as JsonObject }
}.getOrNull()
