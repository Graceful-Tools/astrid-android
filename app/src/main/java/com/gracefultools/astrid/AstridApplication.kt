package com.gracefultools.astrid

import android.app.Application
import com.gracefultools.astrid.platform.BackgroundSync
import com.gracefultools.astrid.platform.NetworkWatch
import com.gracefultools.astrid.platform.ReminderAlarm
import com.gracefultools.astrid.platform.ReminderNotifier

/** Owns the one [AppGraph] — and with it the one core — for the life of the process. */
open class AstridApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = makeGraph()
        NetworkWatch.start(this, graph.core, graph.scope)
        if (schedulesBackgroundWork) BackgroundSync.schedule(this)
        ReminderNotifier.start(this, graph.core, graph.scope)
        if (schedulesBackgroundWork) ReminderAlarm.start(this, graph.core, graph.scope)
    }

    /** The real graph. The instrumented tests replace it with an offline, seeded one. */
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
