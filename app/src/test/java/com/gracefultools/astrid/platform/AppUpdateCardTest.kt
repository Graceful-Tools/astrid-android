package com.gracefultools.astrid.platform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gracefultools.astrid.ui.theme.AstridTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AATD-67: iOS AppUpdateReminderCard's words and its two answers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class AppUpdateCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `AATD-67 the card says a new Astrid is ready, and Update and Not now answer it`() {
        var updates = 0
        var dismissals = 0
        compose.setContent { AstridTheme { AppUpdateCard(onUpdate = { updates++ }, onDismiss = { dismissals++ }) } }
        compose.onNodeWithText("A new Astrid is ready").assertIsDisplayed()
        compose.onNodeWithText("Updating takes a moment and brings the latest fixes.").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, dismissals)
        compose.onNodeWithText("Update").performClick()
        assertEquals(1, updates)
        assertEquals(1, dismissals)
    }
}
