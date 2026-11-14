package com.gracefultools.astrid.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.gracefultools.astrid.model.StatusChoice
import com.gracefultools.astrid.model.StatusOptions
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.DarkColors
import com.gracefultools.astrid.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * AATD-101: a dialog's or sheet's text button reads the accent **ink**, not the accent fill
 * Material's `colorScheme.primary` carries. On Dark the fill is 3.7:1 on the sheet. iOS leaves its
 * AccentColor to the system tint, which goes lighter on Dark for the same reason.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class TextButtonInkTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.textColor(): Color {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    @Test
    fun `AATD-101 a dialog's Cancel on Dark reads the accent ink`() {
        compose.setContent {
            AstridTheme(ThemeMode.DARK) {
                StatusPicker(StatusOptions(columns = listOf(StatusChoice(id = "c1", name = "Doing"))), onPick = {}, onDismiss = {})
            }
        }
        val cancel = compose.onNodeWithText("Cancel").textColor()
        assertNotEquals("Cancel is drawn in the accent fill", DarkColors.accent, cancel)
        assertEquals(DarkColors.accentInk, cancel)
    }

    @Test
    fun `AATD-101 no screen draws a Material TextButton directly`() {
        val roots = listOf("src/main/java/com/gracefultools/astrid/ui", "src/main/java/com/gracefultools/astrid/platform").map(::File)
        roots.forEach { assertTrue("sources not found from ${File(".").absolutePath}", it.isDirectory) }
        val raw = Regex("""(?<![\w])(?:androidx\.compose\.material3\.)?TextButton\(""")
        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" && it.name != "AstridTextButton.kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line -> if (raw.containsMatchIn(line)) "${file.name}:${i + 1}" else null }
            }.toList()
        }
        assertTrue("use AstridTextButton, which reads the accent ink:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
