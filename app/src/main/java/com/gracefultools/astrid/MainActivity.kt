package com.gracefultools.astrid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gracefultools.astrid.ui.AppRoot
import com.gracefultools.astrid.ui.signin.SignInScreen
import com.gracefultools.astrid.ui.signin.SignInState
import com.gracefultools.astrid.ui.signin.SignInViewModel
import com.gracefultools.astrid.ui.theme.AstridTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as AstridApplication
    /** Set once the core has started; nothing here reads it before (AATD-93). */
    private lateinit var graph: AppGraph
    private val signIn: SignInViewModel by viewModels { factory { SignInViewModel(graph.core) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilCoreStarts()
        whenCoreStarted {
            signIn.load()
            handle(intent)
            setContent {
                val theme by graph.preferences.theme.collectAsStateWithLifecycle()
                val rowTitleLines by graph.preferences.rowTitleLines.collectAsStateWithLifecycle()
                AstridTheme(theme) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.gracefultools.astrid.ui.theme.LocalRowTitleLines provides rowTitleLines,
                    ) {
                        SystemBars()
                        Root()
                    }
                }
            }
        }
    }

    /**
     * The core starts off the main thread (AstridApplication, AATD-93). Until it has, the launch
     * window (the system splash) stays: the first frame waits, the main thread does not.
     */
    private fun holdFirstFrameUntilCoreStarts() {
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!::graph.isInitialized) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    /**
     * Runs [block] once the core has started — at once when it already has (a rotation), else in
     * arrival order after it, so an intent that comes while it starts is not handled before the launch.
     */
    private fun whenCoreStarted(block: () -> Unit) {
        lifecycleScope.launch {
            if (!::graph.isInitialized) graph = app.awaitGraph()
            block()
        }
    }

    @Composable
    private fun Root() {
        val state by signIn.state.collectAsStateWithLifecycle()
        val url by signIn.openBrowser.collectAsStateWithLifecycle()
        val look by signIn.look.collectAsStateWithLifecycle()
        LaunchedEffect(url) {
            url?.let { open(it); signIn.browserOpened() }
        }
        if (state == SignInState.Checking) {
            com.gracefultools.astrid.ui.common.SplashLookalike()
        } else if (state == SignInState.SignedIn) {
            com.gracefultools.astrid.platform.NotificationPrompting(graph.notificationPrompt, graph.preferences)
            if (!app.suppressesInterruptingPrompts) {
                com.gracefultools.astrid.platform.ReviewPrompting(graph.reviewPrompt)
                com.gracefultools.astrid.platform.AppUpdatePrompting(app.appUpdatePrompt, app.playUpdates)
            }
            AppRoot(graph = graph, onSignedOut = signIn::signedOut, onDeepLink = deepLinks)
        } else {
            SignInScreen(
                state = state,
                look = look,
                onProvider = signIn::begin,
                onOpenAgain = { signIn.begin(null) },
                onCancel = signIn::cancel,
            )
        }
    }

    /**
     * Status and navigation bar icons follow the APP's theme, not the system's, as iOS forces the
     * interface style to match the theme: dark icons on Ocean and Light, light icons on Dark. The
     * window behind Compose takes the theme's background too, so Dark never flashes Ocean (AATD-33).
     */
    @Composable
    private fun SystemBars() {
        val colors = com.gracefultools.astrid.ui.theme.AstridTheme.colors
        LaunchedEffect(colors.isDark, colors.bgPrimary) {
            val clear = android.graphics.Color.TRANSPARENT
            val style = if (colors.isDark) androidx.activity.SystemBarStyle.dark(clear)
            else androidx.activity.SystemBarStyle.light(clear, clear)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.bgPrimary.toArgb()))
        }
    }

    private val deepLinks = DeepLinks()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        whenCoreStarted { handle(intent) }
    }

    /**
     * The hand-off's callback; a share (a file, or text — an Android extra); the New task
     * shortcut; a tapped reminder; or a link.
     */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> { shared(intent); return }
            com.gracefultools.astrid.platform.ReminderNotifier.ACTION_OPEN -> {
                val ids = intent.getStringArrayExtra(com.gracefultools.astrid.platform.ReminderNotifier.EXTRA_TASKS)
                    ?.filter(DeepLinks::isValidId).orEmpty()
                if (ids.isNotEmpty()) deepLinks.offer(Arrival.Reminder(ids))
                return
            }
        }
        val data = intent.data?.toString() ?: return
        if (SignInViewModel.isCallback(data)) signIn.callback(data) else DeepLinks.parse(data)?.let(deepLinks::offer)
    }

    /**
     * iOS's share extension takes one file; so does this (the first of several). The file is
     * copied into app storage at once, while the sender's grant lasts, off the main thread.
     */
    private fun shared(intent: Intent) {
        val text = DeepLinks.shared(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        val stream: Uri? = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
        } else {
            androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        }
        if (stream == null) { deepLinks.offer(Arrival.NewTask(text)); return }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                com.gracefultools.astrid.ui.detail.copyIntoApp(this@MainActivity, stream)?.let { (path, name, mime) ->
                    SharedFile(path, name, mime, java.io.File(path).length())
                }
            }
            deepLinks.offer(Arrival.NewTask(text, file))
        }
    }

    private fun open(url: String) {
        // A Custom Tab keeps the person in the app's task and shares the browser's sign-ins, which is
        // what makes "Continue with Google" one tap for someone already signed in to Google.
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
    }
}
