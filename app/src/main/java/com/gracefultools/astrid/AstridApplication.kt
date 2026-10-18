package com.gracefultools.astrid

import android.app.Application
import com.gracefultools.astrid.platform.BackgroundSync
import com.gracefultools.astrid.platform.NetworkWatch
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
    }

    /** The real graph. The instrumented tests replace it with an offline, seeded one. */
    protected open fun makeGraph(): AppGraph = AppGraph.start(this)

    /** A UI-test build never reaches the network, so it schedules nothing. */
    protected open val schedulesBackgroundWork: Boolean = true
}
