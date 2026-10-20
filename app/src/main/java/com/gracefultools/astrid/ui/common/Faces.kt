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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme

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
val RowFaceShape = RoundedCornerShape(8.dp)

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
 * iOS's checkbox artwork, `check_box[_repeat][_checked]_<priority>` — the same 16 images, so a
 * done task shows the big green tick and a repeating one the circular arrows (AATD-36).
 */
@Composable
fun CheckboxArt(priority: Int, repeating: Boolean, checked: Boolean, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Image(painterResource(checkboxDrawable(priority, repeating, checked)), null, modifier.size(size))
}

fun checkboxDrawable(priority: Int, repeating: Boolean, checked: Boolean): Int {
    val p = priority.coerceIn(0, 3)
    return when {
        repeating && checked -> listOf(R.drawable.check_box_repeat_checked_0, R.drawable.check_box_repeat_checked_1, R.drawable.check_box_repeat_checked_2, R.drawable.check_box_repeat_checked_3)[p]
        repeating -> listOf(R.drawable.check_box_repeat_0, R.drawable.check_box_repeat_1, R.drawable.check_box_repeat_2, R.drawable.check_box_repeat_3)[p]
        checked -> listOf(R.drawable.check_box_checked_0, R.drawable.check_box_checked_1, R.drawable.check_box_checked_2, R.drawable.check_box_checked_3)[p]
        else -> listOf(R.drawable.check_box_0, R.drawable.check_box_1, R.drawable.check_box_2, R.drawable.check_box_3)[p]
    }
}
