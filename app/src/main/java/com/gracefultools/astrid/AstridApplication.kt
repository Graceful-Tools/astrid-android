package com.gracefultools.astrid

import android.app.Application
import com.gracefultools.astrid.platform.NetworkWatch

/** Owns the one [AppGraph] — and with it the one core — for the life of the process. */
class AstridApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.start(this)
        NetworkWatch.start(this, graph.core, graph.scope)
    }
}
