package com.gracefultools.astrid.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the notification permission stands — iOS's UNAuthorizationStatus, as Android can tell. */
enum class NotificationStatus { AUTHORIZED, NOT_DETERMINED, DENIED }

/** What the app should show: nothing, iOS's "Enable Push Notifications", or "Notifications Disabled". */
enum class PromptDecision { NONE, ASK, OPEN_SETTINGS }

/** The counts the prompt is throttled by, kept per device (iOS: UserDefaults). */
interface PromptLedger {
    var promptCount: Int
    var lastPromptMillis: Long?
}

/**
 * iOS NotificationPromptManager: ask after sign-in and after a due date is set, at most three
 * times, then once a month; when the permission was refused, offer Settings instead (AATD-54).
 */
class NotificationPrompt(private val ledger: PromptLedger, private val now: () -> Long = System::currentTimeMillis) {
    private val _showing = MutableStateFlow(PromptDecision.NONE)
    /** The dialog to show now; back to NONE when it is dismissed. */
    val showing: StateFlow<PromptDecision> = _showing.asStateFlow()

    /** iOS shouldPromptForNotifications. */
    fun decide(status: NotificationStatus): PromptDecision {
        if (status == NotificationStatus.AUTHORIZED) return PromptDecision.NONE
        val count = ledger.promptCount
        val last = ledger.lastPromptMillis
        val allowed = when {
            count < MAX_INITIAL_PROMPTS -> true
            last != null -> (now() - last) / DAY_MILLIS >= MONTHLY_INTERVAL_DAYS
            else -> true
        }
        if (!allowed) return PromptDecision.NONE
        return if (status == NotificationStatus.DENIED) PromptDecision.OPEN_SETTINGS else PromptDecision.ASK
    }

    /** iOS checkAndPromptAfterDateSet: decide, count the attempt, and raise the dialog. */
    fun check(status: NotificationStatus) {
        val decision = decide(status)
        if (decision == PromptDecision.NONE) return
        ledger.promptCount = ledger.promptCount + 1
        ledger.lastPromptMillis = now()
        _showing.value = decision
    }

    fun dismiss() { _showing.value = PromptDecision.NONE }

    private val _requests = MutableStateFlow(0)
    /** Bumped when something worth a reminder happened (a due date was set); the activity checks. */
    val requests: StateFlow<Int> = _requests.asStateFlow()
    fun request() { _requests.value += 1 }

    companion object {
        const val MAX_INITIAL_PROMPTS = 3
        const val MONTHLY_INTERVAL_DAYS = 30
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
