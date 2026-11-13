package com.gracefultools.astrid

import android.app.Application
import com.gracefultools.astrid.platform.BackgroundSync
import com.gracefultools.astrid.platform.NetworkWatch
import com.gracefultools.astrid.platform.ReminderAlarm
import com.gracefultools.astrid.platform.ReminderNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/** Owns the one [AppGraph] — and with it the one core — for the life of the process. */
open class AstridApplication : Application() {
    private val graphStarted = CompletableDeferred<AppGraph>()

    /**
     * The graph, once the core has started. The core starts on a thread of its own (AATD-93), so a
     * caller that comes before it — a receiver, the sync worker — waits here; the activity instead
     * keeps the launch window up and asks [awaitGraph].
     */
    val graph: AppGraph get() = runBlocking { graphStarted.await() }

    /** Whether the core has started, so [graph] answers at once. */
    val isGraphReady: Boolean get() = graphStarted.isCompleted

    suspend fun awaitGraph(): AppGraph = graphStarted.await()

    override fun onCreate() {
        super.onCreate()
        // Loading the core is a JNA load, a dlopen of the 16 MB library, a SQLite open and a Tokio
        // pool: on the main thread it held the first frame (AATD-93).
        thread(name = "astrid-core-start") { start() }
    }

    private fun start() {
        val graph = try {
            makeGraph().also {
                NetworkWatch.start(this, it.core, it.scope)
                if (schedulesBackgroundWork) BackgroundSync.schedule(this)
                ReminderNotifier.start(this, it.core, it.scope)
                if (schedulesBackgroundWork) ReminderAlarm.start(this, it.core, it.scope)
            }
        } catch (failure: Throwable) {
            // Whoever waits fails too, and the process still dies of it, as it did on the main thread.
            graphStarted.completeExceptionally(failure)
            throw failure
        }
        graphStarted.complete(graph)
    }

    /** The real graph, made off the main thread. The instrumented tests replace it with an offline, seeded one. */
    protected open fun makeGraph(): AppGraph = AppGraph.start(this)

    /**
     * Whether prompts that cover the screen unasked (the review ask) stay away — a UI-test run, as
     * iOS's UITestSession.suppressesInterruptingPrompts: an alert over the app makes it untappable.
     */
    open val suppressesInterruptingPrompts: Boolean = false

    /**
     * iOS AppUpdateService.shared, asked through Google Play (AATD-67). One per process, so it
     * checks once per launch rather than once per rotation; made on first use, never in a UI test.
     */
    val playUpdates by lazy { com.gracefultools.astrid.platform.PlayUpdates(this) }
    val appUpdatePrompt by lazy { com.gracefultools.astrid.platform.AppUpdatePrompt(graph.preferences, playUpdates) }

    /** A UI-test build never reaches the network, so it schedules nothing. */
    protected open val schedulesBackgroundWork: Boolean = true
}
