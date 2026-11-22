package com.gracefultools.astrid.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridColors
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.TouchSize

/**
 * A person's face, as iOS draws it everywhere: their photo when there is one, else their initials
 * on the accent colour. [shape] is iOS's — a rounded 8 square on rows and the leading control, a
 * circle in pickers, comments and the sidebar.
 */
@Composable
fun Avatar(
    image: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    border: Color? = null,
    borderWidth: Dp = 2.dp,
) {
    val colors = AstridTheme.colors
    var failed by remember(image) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(shape).background(colors.accent)
            .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image.isNullOrBlank() || failed) {
            Text(initials.ifBlank { "?" }, color = colors.accentText, fontSize = (size.value * 0.41f).sp, fontWeight = FontWeight.SemiBold)
        } else {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                onError = { failed = true }, modifier = Modifier.size(size).clip(shape),
            )
            if (border != null) Box(Modifier.size(size).border(borderWidth, border, shape))
        }
    }
}

/** A row's rounded-8 square face. */
val RowFaceShape = RoundedCornerShape(Radius.medium)

/**
 * iOS's User.initials: two words give a letter each, one word its first two letters, no name the
 * email's first two characters, and nothing at all a neutral dot (never "??", which reads as an error).
 */
fun initialsOf(name: String?, email: String? = null): String {
    if (name != null) {
        val words = name.split(' ').filter { it.isNotEmpty() }
        return if (words.size >= 2) (words[0].take(1) + words[1].take(1)).uppercase() else name.take(2).uppercase()
    }
    return email?.take(2)?.uppercase() ?: "\u2022"
}

/**
 * The task checkbox, astrid-core's art (register DS21, ADEV-196), as iOS's TaskCheckboxArt and
 * web's CheckboxArt draw it: the box, or the repeat arrows, tinted with [checkboxTint], then the
 * check in its own green once done. The layers are vendored from the pinned core
 * (`scripts/check-design-tokens.sh`); never edit them here. They replaced iOS's 16 fixed-colour
 * PNGs (AATD-36), whose low-priority box was #328ACC whatever the accent or theme.
 */
@Composable
fun CheckboxArt(priority: Int, repeating: Boolean, checked: Boolean, modifier: Modifier = Modifier, size: Dp = TouchSize.leadingControl) {
    val tint = checkboxTint(priority, AstridTheme.colors)
    Box(modifier.size(size)) {
        for (layer in checkboxLayers(checked, repeating)) {
            Image(
                painterResource(layer), null, Modifier.size(size),
                colorFilter = if (layer == R.drawable.checkbox_check) null else ColorFilter.tint(tint),
            )
        }
    }
}

/** The layers, bottom first: the box, or the arrows for a repeating task, then the check once done. */
fun checkboxLayers(checked: Boolean, repeating: Boolean): List<Int> =
    listOf(if (repeating) R.drawable.checkbox_repeat else R.drawable.checkbox_box) +
        if (checked) listOf(R.drawable.checkbox_check) else emptyList()

/**
 * The box's colour: the priority token (low is the accent, or its lighter ink on Dark). No
 * priority is the theme's muted text, as on iOS and web: `priority-none` is 3:1 or less on Dark's
 * card, and most tasks have none.
 */
fun checkboxTint(priority: Int, colors: AstridColors): Color = when (priority) {
    1 -> colors.tokens.priorityLow
    2 -> colors.tokens.priorityMedium
    3 -> colors.tokens.priorityHigh
    else -> colors.textMuted
}
