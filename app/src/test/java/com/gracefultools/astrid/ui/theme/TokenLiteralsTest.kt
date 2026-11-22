package com.gracefultools.astrid.ui.theme

import androidx.compose.ui.graphics.Color
import com.gracefultools.astrid.ui.tasks.parseColor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADEV-196: colours that were typed as hex now come from the tokens, at the same values. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TokenLiteralsTest {
    @Test
    fun `ADEV-196 a list with no colour or an unreadable one is tokens category-blue`() {
        val blue = LightColors.tokens.categoryBlue
        assertEquals(Color(0xFF3B82F6), blue)
        assertEquals(blue, parseColor(null))
        assertEquals(blue, parseColor(""))
        assertEquals(blue, parseColor("not a colour"))
        assertEquals(Color(0xFFEF4444), parseColor("#ef4444"))
    }

    @Test
    fun `ADEV-196 the speech bubble's pointer tokens keep their values`() {
        assertEquals(Color(0xFF4B5563), DarkColors.border)
        assertEquals(Color(0xFFD1D5DB), LightColors.borderHover)
        assertEquals(Color(0xFFF97316), LightColors.tokens.categoryOrange)
    }
}
