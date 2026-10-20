package com.gracefultools.astrid.platform

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.command
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** iOS ReviewPromptManager's UserDefaults, kept per device. */
interface ReviewLedger {
    var reviewPromptCount: Int
    var lastReviewPromptMillis: Long?
    var hasReviewed: Boolean
}

/** Which of iOS's two alerts is up: "Loving Astrid?", then "Help Us Improve" after "Not really". */
enum class ReviewDialog { NONE, LOVE, FEEDBACK }

/**
 * iOS ReviewPromptManager (AATD-63): ask "Loving Astrid?" once the reader has completed five tasks,
 * at most every 30 days, and never again once they said yes. "Yes" goes to the store's own review
 * flow; "Not really" offers Report an Issue or Send Feedback.
 *
 * The completed count is the core's `profileStats` — the server's number for the account. iOS
 * counts the completed tasks it holds; the shell does not count tasks itself.
 */
class ReviewPrompt(
    private val ledger: ReviewLedger,
    private val core: Core,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _showing = MutableStateFlow(ReviewDialog.NONE)
    val showing: StateFlow<ReviewDialog> = _showing.asStateFlow()

    /** iOS shouldPromptForReview. */
    suspend fun isDue(): Boolean {
        if (ledger.hasReviewed) return false
        ledger.lastReviewPromptMillis?.let { last -> if ((now() - last) / DAY_MILLIS < MIN_DAYS_BETWEEN_PROMPTS) return false }
        return (completedTasks() ?: return false) >= MIN_COMPLETED_TASKS
    }

    /** iOS checkAndPromptForReview: on sign-in (after a pause) and when the app comes back. */
    suspend fun check() {
        if (_showing.value != ReviewDialog.NONE || !isDue()) return
        _showing.value = ReviewDialog.LOVE
        ledger.reviewPromptCount = ledger.reviewPromptCount + 1
        ledger.lastReviewPromptMillis = now()
    }

    /** "Yes, I love it!": never asked again; the caller opens the store's review flow. */
    fun love() {
        ledger.hasReviewed = true
        _showing.value = ReviewDialog.NONE
    }

    /** "Not really": iOS's Help Us Improve. */
    fun notReally() { _showing.value = ReviewDialog.FEEDBACK }

    fun dismiss() { _showing.value = ReviewDialog.NONE }

    private suspend fun completedTasks(): Int? = runCatching {
        ((core.run(command("profileStats")) as? JsonObject)?.get("completed") as? JsonPrimitive)?.intOrNull
    }.getOrNull()

    companion object {
        const val MIN_DAYS_BETWEEN_PROMPTS = 30
        const val MIN_COMPLETED_TASKS = 5
        /** iOS Brand.supportEmail and its "<app> App Feedback" subject. */
        const val SUPPORT_EMAIL = "support@astrid.cc"
        const val FEEDBACK_SUBJECT = "Astrid App Feedback"
        /** Where Report an Issue goes until the core names the Bugs & Feedback list (see PARITY). */
        const val REPORT_ISSUE_URL = "https://astrid.cc"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** iOS openSupportEmail's address, subject percent-encoded. */
        fun feedbackMailto(): String = "mailto:$SUPPORT_EMAIL?subject=" + FEEDBACK_SUBJECT.replace(" ", "%20")
    }
}
