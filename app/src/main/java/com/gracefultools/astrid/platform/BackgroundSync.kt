package com.gracefultools.astrid.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gracefultools.astrid.AstridApplication
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.command
import java.util.concurrent.TimeUnit

/**
 * iOS's BGTask: a pass that keeps the cache current and sends the Outbox while the app is not on
 * screen. WorkManager decides when, on a network, no more often than every 15 minutes.
 */
object BackgroundSync {
    private const val NAME = "astrid.sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * One pass: send what is waiting, then pull what moved. Answers whether WorkManager should try
     * again — never for a signed-out session, which no retry can fix.
     */
    suspend fun pass(core: Core): Outcome {
        return try {
            core.run(command("drain"))
            core.run(command("sync"))
            Outcome.DONE
        } catch (failure: CoreFailure) {
            when {
                failure.needsSignIn -> Outcome.GIVE_UP
                failure.isStillPending -> Outcome.RETRY
                else -> Outcome.RETRY
            }
        }
    }

    enum class Outcome { DONE, RETRY, GIVE_UP }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as AstridApplication).graph
        val outcome = BackgroundSync.pass(graph.core)
        // The app may not be running when a reminder comes due; this pass is when it is told.
        ReminderNotifier.forContext(applicationContext, graph.core).postDue()
        return when (outcome) {
            BackgroundSync.Outcome.DONE, BackgroundSync.Outcome.GIVE_UP -> Result.success()
            BackgroundSync.Outcome.RETRY -> Result.retry()
        }
    }
}
