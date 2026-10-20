package com.gracefultools.astrid

import android.content.Context
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreSession
import com.gracefultools.astrid.platform.KeystoreCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Everything the screens share, made once. Hand-wired rather than a DI framework: there are a
 * handful of objects and each is made in exactly one place.
 */
class AppGraph(
    val core: Core,
    val scope: CoroutineScope,
    val preferences: Preferences,
) {
    /** iOS NotificationPromptManager: one for the app, so a due date set anywhere can ask. */
    val notificationPrompt = com.gracefultools.astrid.platform.NotificationPrompt(preferences)

    companion object {
        fun start(context: Context): AppGraph {
            val dir = context.filesDir
            val core = CoreSession.start(
                cachePath = dir.resolve("astrid.sqlite").absolutePath,
                credentials = KeystoreCredentialStore(dir.resolve("astrid.credentials")),
                background = true,
            )
            return AppGraph(
                core = core,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                preferences = Preferences(context),
            )
        }
    }
}
