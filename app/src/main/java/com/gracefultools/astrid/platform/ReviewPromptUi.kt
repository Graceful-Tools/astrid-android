package com.gracefultools.astrid.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.review.ReviewManagerFactory
import com.gracefultools.astrid.R
import kotlinx.coroutines.launch

/**
 * iOS AstridApp's two review alerts (AATD-63): "Loving Astrid?" — "Not really" / "Yes, I love it!" —
 * and "Help Us Improve" — "Maybe Later" / "Report an Issue" / "Send Feedback". Checked five seconds
 * after the signed-in app appears and whenever it comes back to the foreground, as iOS does.
 */
@Composable
fun ReviewPrompting(prompt: ReviewPrompt) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val showing by prompt.showing.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000)
        prompt.check()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { owner.lifecycleScope.launch { prompt.check() } }

    when (showing) {
        ReviewDialog.NONE -> Unit
        ReviewDialog.LOVE -> AlertDialog(
            onDismissRequest = prompt::dismiss,
            title = { Text(stringResource(R.string.review_loving_title)) },
            text = { Text(stringResource(R.string.review_loving_message)) },
            dismissButton = { TextButton(onClick = prompt::notReally) { Text(stringResource(R.string.review_not_really)) } },
            confirmButton = {
                TextButton(onClick = { prompt.love(); requestStoreReview(activity) }) { Text(stringResource(R.string.review_love_it)) }
            },
        )
        ReviewDialog.FEEDBACK -> AlertDialog(
            onDismissRequest = prompt::dismiss,
            title = { Text(stringResource(R.string.review_help_us_improve)) },
            text = { Text(stringResource(R.string.review_help_message)) },
            // Three choices: Android's dialog has two button slots, so the two routes stack in one.
            confirmButton = {
                Column {
                    TextButton(onClick = { prompt.dismiss(); reportIssue(context) }) { Text(stringResource(R.string.review_report_issue)) }
                    TextButton(onClick = { prompt.dismiss(); sendFeedback(context) }) { Text(stringResource(R.string.review_send_feedback)) }
                    TextButton(onClick = prompt::dismiss) { Text(stringResource(R.string.review_maybe_later)) }
                }
            },
        )
    }
}

/** Google Play's In-App Review flow; the Play listing when the flow cannot start (iOS: AppStore.requestReview). */
fun requestStoreReview(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (request.isSuccessful) manager.launchReviewFlow(activity, request.result)
        else openPlayListing(activity)
    }
}

internal fun openPlayListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** iOS openSupportEmail: a mail to Brand.supportEmail (support@astrid.cc), "Astrid App Feedback". */
fun sendFeedback(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(ReviewPrompt.feedbackMailto())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** iOS openFeedbackForm opens the Bugs & Feedback list; the core does not name it yet, so the site. */
fun reportIssue(context: Context) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(ReviewPrompt.REPORT_ISSUE_URL)) }
}
