package com.gracefultools.astrid.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.TradeoffPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AATD-103: the core's `tradeoffGrid` block (astrid-core af42ba53, AWTD-1626), drawn as iOS
 * `TradeOffGridView` draws it. The blocks are the core fixture's `grid*` cases
 * (`contracts/fixtures/markdown.json` at the pinned revision), shortened to the fields that matter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class TradeoffGridTest {
    @get:Rule val compose = createComposeRule()

    private fun t(text: String) = """{"kind":"text","text":"$text"}"""
    private fun cell(rating: String?, text: String?) =
        """{"rating":${rating?.let { "\"$it\"" } ?: "null"},"inlines":[${text?.let(::t) ?: ""}]}"""

    /** Fixture `grid`: `| A. Lisbon | ++ cheap | - long flight |` / `| B. Porto | ~ fair | + short |`. */
    private val grid = """[{"kind":"tradeoffGrid","alignments":["none","none","none"],
        "header":[[${t("Option")}],[${t("Cost")}],[${t("Time")}]],
        "rows":[{"name":[${t("Lisbon")}],"cells":[${cell("veryGood", "cheap")},${cell("poor", "long flight")}]},
                {"name":[${t("Porto")}],"cells":[${cell("mixed", "fair")},${cell("good", "short")}]}],
        "key":["poor","mixed","good","veryGood"]}]"""

    /** ADEV-184: one cell at each extreme. */
    private val extremes = """[{"kind":"tradeoffGrid","alignments":["none","none"],
        "header":[[${t("Option")}],[${t("Cost")}]],
        "rows":[{"name":[${t("Lisbon")}],"cells":[${cell("veryGood", "cheap")}]},
                {"name":[${t("Moscow")}],"cells":[${cell("veryPoor", "no visa")}]}],
        "key":["veryPoor","veryGood"]}]"""

    /** Fixture `grid-empty-cell`, with the second row written one cell short. */
    private val emptyCells = """[{"kind":"tradeoffGrid","alignments":["none","none","none"],
        "header":[[${t("Option")}],[${t("Cost")}],[${t("Time")}]],
        "rows":[{"name":[${t("One")}],"cells":[${cell("good", "cheap")},${cell(null, null)}]},
                {"name":[${t("Two")}],"cells":[${cell(null, null)}]}],
        "key":["good"]}]"""

    private fun show(json: String) {
        val blocks = (Json.parseToJsonElement(json) as JsonArray).map { it as JsonObject }
        compose.setContent { AstridTheme { MarkdownView(blocks) } }
    }

    @Test
    fun `AATD-103 a trade-off grid numbers its options and letters its criteria`() {
        show(grid)
        compose.onNodeWithText("1. Lisbon").assertIsDisplayed()
        compose.onNodeWithText("2. Porto").assertIsDisplayed()
        compose.onNodeWithText("A  Cost").assertIsDisplayed()
        compose.onNodeWithText("B  Time").assertIsDisplayed()
    }

    @Test
    fun `AATD-103 each rated cell says its rating, then its reason`() {
        show(grid)
        compose.onNodeWithContentDescription("Decisive. cheap").assertIsDisplayed()
        compose.onNodeWithContentDescription("Poor. long flight").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mixed. fair").assertIsDisplayed()
        compose.onNodeWithContentDescription("Good. short").assertIsDisplayed()
    }

    @Test
    fun `AATD-103 the colour key lists the core's key, worst to best`() {
        show(grid)
        val key = compose.onNodeWithTag("tradeoff:key").fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Poor", "Mixed", "Good", "Decisive"), key)
    }

    @Test
    fun `ADEV-184 the extremes read Decisive and Disqualifying, as the core catalog says`() {
        show(extremes)
        compose.onNodeWithContentDescription("Decisive. cheap").assertIsDisplayed()
        compose.onNodeWithContentDescription("Disqualifying. no visa").assertIsDisplayed()
        val key = compose.onNodeWithTag("tradeoff:key").fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Disqualifying", "Decisive"), key)
    }

    @Test
    @Config(qualifiers = "de-w400dp-h900dp")
    fun `ADEV-184 the extremes take the core catalog's translation`() {
        show(extremes)
        compose.onNodeWithContentDescription("Entscheidend. cheap").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ausschlusskriterium. no visa").assertIsDisplayed()
    }

    @Test
    fun `AATD-103 an empty cell and a short row's missing cell are drawn unrated`() {
        show(emptyCells)
        compose.onNodeWithContentDescription("Good. cheap").assertIsDisplayed()
        assertEquals(3, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Not rated.")).fetchSemanticsNodes().size)
    }

    @Test
    fun `AATD-103 Full screen opens the grid on a screen of its own`() {
        show(grid)
        compose.onNodeWithText("Full screen").performClick()
        compose.onNodeWithTag("tradeoff:fullScreen").assertIsDisplayed()
    }

    @Test
    fun `AATD-103 the cell colours are the core palette's tradeoff colours`() {
        assertEquals(
            listOf(Color(0xFFCC0000), Color(0xFFE06666), Color(0xFFFFD966), Color(0xFF93C47D), Color(0xFF38761D)),
            listOf("veryPoor", "poor", "mixed", "good", "veryGood").map { TradeoffPalette.fill(it) },
        )
        assertEquals(
            listOf(Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF111827), Color(0xFF111827), Color(0xFFFFFFFF)),
            listOf("veryPoor", "poor", "mixed", "good", "veryGood").map { TradeoffPalette.text(it) },
        )
    }
}
