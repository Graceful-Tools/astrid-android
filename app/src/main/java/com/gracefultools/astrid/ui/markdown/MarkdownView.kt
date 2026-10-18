package com.gracefultools.astrid.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Draws the core's markdown blocks (`astrid_core::markdown`, fixture-locked to the web's renderer)
 * — iOS's shared MarkdownView. The core decides what the text means; this only draws blocks.
 */
@Composable
fun MarkdownView(blocks: List<JsonObject>, modifier: Modifier = Modifier, onReference: (String, String) -> Unit = { _, _ -> }) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) Block(block, onReference)
    }
}

private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.arr(name: String) = (this[name] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

@Composable
private fun Block(block: JsonObject, onReference: (String, String) -> Unit) {
    val colors = AstridTheme.colors
    when (block.str("kind")) {
        "paragraph" -> Inlines(block.arr("inlines"), 16, FontWeight.Normal, onReference)
        "heading" -> {
            val level = (block["level"] as? JsonPrimitive)?.intOrNull ?: 1
            Inlines(block.arr("inlines"), listOf(24, 21, 19, 17, 16, 15).getOrElse(level - 1) { 16 }, FontWeight.SemiBold, onReference)
        }
        "code" -> Text(
            block.str("text").orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().background(colors.bgTertiary, RoundedCornerShape(6.dp))
                .horizontalScroll(rememberScrollState()).padding(10.dp),
        )
        "list" -> {
            val ordered = (block["ordered"] as? JsonPrimitive)?.booleanOrNull == true
            val start = (block["start"] as? JsonPrimitive)?.intOrNull ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.arr("items").forEachIndexed { index, item ->
                    val checked = (item["checked"] as? JsonPrimitive)?.booleanOrNull
                    val marker = when {
                        checked == true -> "☑"
                        checked == false -> "☐"
                        ordered -> "${start + index}."
                        else -> "•"
                    }
                    Row {
                        Text(marker, color = colors.textSecondary, modifier = Modifier.width(24.dp))
                        MarkdownView(item.arr("blocks"), onReference = onReference)
                    }
                }
            }
        }
        "quote" -> Row {
            Column(Modifier.width(3.dp).background(colors.border)) {}
            MarkdownView(block.arr("blocks"), Modifier.padding(start = 10.dp), onReference)
        }
        "rule" -> HorizontalDivider(color = colors.border)
        "table" -> Column(Modifier.horizontalScroll(rememberScrollState())) {
            val header = (block["header"] as? JsonArray)?.map { cell -> (cell as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList() } ?: emptyList()
            Row { header.forEach { Inlines(it, 15, FontWeight.SemiBold, onReference, Modifier.width(120.dp).padding(4.dp)) } }
            (block["rows"] as? JsonArray)?.forEach { row ->
                Row {
                    (row as? JsonArray)?.forEach { cell ->
                        Inlines((cell as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList(), 15, FontWeight.Normal, onReference, Modifier.width(120.dp).padding(4.dp))
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun Inlines(inlines: List<JsonObject>, size: Int, weight: FontWeight, onReference: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    val uri = LocalUriHandler.current
    val text: AnnotatedString = buildAnnotatedString {
        for (inline in inlines) {
            when (inline.str("kind")) {
                "text" -> {
                    fun flag(name: String) = (inline[name] as? JsonPrimitive)?.booleanOrNull == true
                    val style = SpanStyle(
                        fontWeight = if (flag("bold")) FontWeight.Bold else null,
                        fontStyle = if (flag("italic")) FontStyle.Italic else null,
                        textDecoration = if (flag("strike")) TextDecoration.LineThrough else null,
                        fontFamily = if (flag("code")) FontFamily.Monospace else null,
                    )
                    val link = inline.str("link")
                    if (link != null) {
                        withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)))) {
                            withStyle(style) { append(inline.str("text").orEmpty()) }
                        }
                    } else {
                        withStyle(style) { append(inline.str("text").orEmpty()) }
                    }
                }
                "reference" -> {
                    val kind = inline.str("reference").orEmpty()
                    val id = inline.str("id").orEmpty()
                    withLink(
                        LinkAnnotation.Clickable("$kind:$id", TextLinkStyles(SpanStyle(color = colors.accent))) { onReference(kind, id) },
                    ) { append(inline.str("label").orEmpty()) }
                }
                "lineBreak" -> append("\n")
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = uri
    Text(text, fontSize = size.sp, fontWeight = weight, color = colors.textPrimary, modifier = modifier)
}
