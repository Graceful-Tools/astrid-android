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
import com.gracefultools.astrid.ui.AppRoot
import com.gracefultools.astrid.ui.signin.SignInScreen
import com.gracefultools.astrid.ui.signin.SignInState
import com.gracefultools.astrid.ui.signin.SignInViewModel
import com.gracefultools.astrid.ui.theme.AstridTheme

class MainActivity : ComponentActivity() {
    private val graph get() = (application as AstridApplication).graph
    private val signIn: SignInViewModel by viewModels { factory { SignInViewModel(graph.core) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        signIn.load()
        handle(intent)
        setContent {
            val theme by graph.preferences.theme.collectAsStateWithLifecycle()
            AstridTheme(theme) {
                SystemBars()
                Root()
            }
        }
    }

    @Composable
    private fun Root() {
        val state by signIn.state.collectAsStateWithLifecycle()
        val url by signIn.openBrowser.collectAsStateWithLifecycle()
        LaunchedEffect(url) {
            url?.let { open(it); signIn.browserOpened() }
        }
        if (state == SignInState.Checking) {
            com.gracefultools.astrid.ui.common.SplashLookalike()
        } else if (state == SignInState.SignedIn) {
            NotificationPermission()
            AppRoot(graph = graph, onSignedOut = signIn::signedOut, onDeepLink = deepLinks)
        } else {
            SignInScreen(
                state = state,
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

    /** Reminders need it on Android 13+; asked once, when there is first something to remind about. */
    @Composable
    private fun NotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val deepLinks = DeepLinks()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The hand-off's callback, a shared text, the New task shortcut, or a task link. */
    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = DeepLinks.shared(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            deepLinks.offer(Arrival.NewTask(text))
            return
        }
        val data = intent.data?.toString() ?: return
        if (SignInViewModel.isCallback(data)) signIn.callback(data) else DeepLinks.parse(data)?.let(deepLinks::offer)
    }

    private fun open(url: String) {
        // A Custom Tab keeps the person in the app's task and shares the browser's sign-ins, which is
        // what makes "Continue with Google" one tap for someone already signed in to Google.
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
    }
}
