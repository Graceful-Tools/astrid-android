package com.gracefultools.astrid.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS AppUpdateService's UserDefaults: the version "Not now" was said to, kept per device. */
interface UpdateLedger {
    var dismissedUpdateVersion: Int?
}

/** A newer build Google Play will install. Play names it by version code, not version name. */
data class AvailableUpdate(val versionCode: Int)

/**
 * Who knows whether a newer build is published: Google Play (see AppUpdateUi). Null is "nothing to
 * offer"; so is a throw.
 */
fun interface UpdateSource {
    suspend fun latest(): AvailableUpdate?
}

/**
 * iOS AppUpdateService (AATD-67, iOS AITD-383): knows whether a newer version has shipped, and
 * remembers being told to be quiet.
 *
 * THE STORE DECIDES, NOT THE SHELL. iOS asks astrid-web what is on the App Store and compares; here
 * Google Play answers "is there an update" for this very install, so no version is compared.
 *
 * FAILURE IS SILENCE, as on iOS: not installed from Play, no Play services, offline — all mean "we
 * do not know", and none may produce a card, an error or a retry.
 */
class AppUpdatePrompt(
    private val ledger: UpdateLedger,
    private val source: UpdateSource,
) {
    private val _available = MutableStateFlow<AvailableUpdate?>(null)

    /** The update to offer, or null for "nothing to say". */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    /** One check per launch is plenty: a release does not land while you are looking at the app. */
    private var checked = false

    /** Ask Play, and decide. Safe to call on every appearance; it only works once. */
    suspend fun check() {
        if (checked) return
        checked = true
        val update = runCatching { source.latest() }.getOrNull() ?: return
        if (update.versionCode == ledger.dismissedUpdateVersion) return
        _available.value = update
    }

    /** "Not now" — for this version only. Dismissing one build must not swallow the next. */
    fun dismiss() {
        _available.value?.let { ledger.dismissedUpdateVersion = it.versionCode }
        _available.value = null
    }

    /** Clear the card once the update has started, so it is not waiting when they return. */
    fun acknowledgeUpdateOpened() = dismiss()
}
