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
            AstridTheme(theme) { Root() }
        }
    }

    @Composable
    private fun Root() {
        val state by signIn.state.collectAsStateWithLifecycle()
        val url by signIn.openBrowser.collectAsStateWithLifecycle()
        LaunchedEffect(url) {
            url?.let { open(it); signIn.browserOpened() }
        }
        if (state == SignInState.SignedIn) {
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

    private val deepLinks = DeepLinks()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The hand-off's callback, a shared text, or an `astrid.cc/t/<id>` link. */
    private fun handle(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (SignInViewModel.isCallback(data)) signIn.callback(data) else deepLinks.offer(data)
    }

    private fun open(url: String) {
        // A Custom Tab keeps the person in the app's task and shares the browser's sign-ins, which is
        // what makes "Continue with Google" one tap for someone already signed in to Google.
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
    }
}
