package com.gracefultools.astrid.ui.agents

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.ui.theme.AstridTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AATD-95: a Custom Agent's avatar is the button that changes its photo, as on the web. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h900dp")
class CustomAgentPhotoScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun core() = FakeCore().apply {
        answer("customAgents", """[{"id":"a1","email":"buddy.oc@astrid.cc","name":"buddy","agentName":"buddy","status":"active","registeredAt":"2026-09-01T10:00:00Z"}]""")
        answer("setCustomAgentPhoto", """{"image":"https://cdn.test/buddy.png"}""")
    }

    @Test
    fun `AATD-95 the avatar changes the photo, and a stored photo says so`() {
        val vm = CustomAgentsViewModel(core(), UnconfinedTestDispatcher()).apply { load() }
        compose.setContent { AstridTheme { CustomAgentsScreen(vm, onBack = {}) } }
        val avatar = compose.onNode(hasTestTag("customAgentPhoto-a1"))
        avatar.assertHasClickAction()
        assertEquals("Change profile photo", avatar.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)

        vm.setPhoto("a1", "/cache/picked-photo-1.jpg")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Profile photo updated").fetchSemanticsNodes().isNotEmpty() }
    }
}
