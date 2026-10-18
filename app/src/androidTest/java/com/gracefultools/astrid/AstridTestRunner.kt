package com.gracefultools.astrid

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Runs the instrumented tests against [TestAstridApplication]: the real core, offline and seeded. */
class AstridTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestAstridApplication::class.java.name, context)
}
