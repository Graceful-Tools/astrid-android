package com.gracefultools.astrid.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The package under test: the build's own, so a brand build profiles itself. */
private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.gracefultools.astrid"

/**
 * The startup path the profile covers (AATD-93): process start, the core's start, the first frame.
 * A fresh install is signed out, so that frame is the sign-in screen, as for every new user.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }
}

/** Cold start with no ahead-of-time compilation and with the baseline profile, for the task's numbers. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartNoCompilation() = coldStart(CompilationMode.None())

    @Test fun coldStartBaselineProfile() = coldStart(CompilationMode.Partial())

    private fun coldStart(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
