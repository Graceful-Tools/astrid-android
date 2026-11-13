package com.gracefultools.astrid

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AATD-93: the core is a JNA load, a `dlopen` of a 16 MB library, a SQLite open and a Tokio pool.
 * Started in `Application.onCreate` it held the main thread before the first frame, so it starts on
 * a thread of its own and the launch window waits for it instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StartupTest.SlowStartApp::class)
class StartupTest {
    /** A graph that is not ready until the test says so — the core's start, slowed down to be seen. */
    class SlowStartApp : AstridApplication() {
        override val schedulesBackgroundWork = false
        val release = CountDownLatch(1)
        @Volatile var startedOn: Thread? = null

        override fun makeGraph(): AppGraph {
            startedOn = Thread.currentThread()
            // Bounded, so the old synchronous start fails this test rather than hanging it.
            release.await(5, TimeUnit.SECONDS)
            return AppGraph(FakeCore(), CoroutineScope(SupervisorJob() + Dispatchers.Default), Preferences(this))
        }
    }

    private val app get() = ApplicationProvider.getApplicationContext<SlowStartApp>()

    @After fun release() = app.release.countDown()

    @Test
    fun `AATD-93 the core starts off the main thread`() {
        app.release.countDown()
        val graph = app.graph // waits for the start
        assertNotNull(graph.core)
        assertNotSame("the core started on the main thread", Looper.getMainLooper().thread, app.startedOn)
    }

    @Test
    fun `AATD-93 onCreate returns before the core is ready`() {
        assertFalse("onCreate waited for the core", app.isGraphReady)
    }

    @Test
    fun `AATD-93 the launch screen opens without waiting for the core, and fills in once it has`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse("the activity waited for the core", app.isGraphReady)
        assertEquals("content before the core started", 0, activity.findViewById<android.view.ViewGroup>(android.R.id.content).childCount)

        app.release.countDown()
        app.graph
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, activity.findViewById<android.view.ViewGroup>(android.R.id.content).childCount)
    }
}
