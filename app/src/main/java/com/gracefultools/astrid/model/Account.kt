package com.gracefultools.astrid.model

import kotlinx.serialization.Serializable

/**
 * The signed-in user as `currentUser` answers it (astrid-rules `model::User`): who they are, when
 * they joined, and the verification state `GET /users/me` merges in. Only the signed-in user
 * carries the verification fields.
 */
@Serializable
data class AccountUser(
    val id: String = "",
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    val createdAt: String? = null,
    val verified: Boolean? = null,
    val verifiedViaOAuth: Boolean? = null,
    val hasPendingChange: Boolean? = null,
    val hasPendingVerification: Boolean? = null,
    val pendingEmail: String? = null,
    val isAIAgent: Boolean? = null,
) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: id
}

/** A write the server refused, with its reason (`outboxStats.deadLetters`). */
@Serializable
data class DeadLetter(val kind: String = "", val error: String? = null)

/** `outboxStats`: the journal's health. */
@Serializable
data class OutboxStats(
    val pending: Int = 0,
    val running: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val hasUnsentWork: Boolean = false,
    val deadLetters: List<DeadLetter> = emptyList(),
    /**
     * When the last sync pass finished. The core keeps it (`sync.last-completed`) but no command
     * answers it yet; read here so the row appears the day `outboxStats` carries it.
     */
    val lastSync: String? = null,
) {
    val isHealthy: Boolean get() = failed == 0
}

/** `features`: this person's flags. Null is "never asked", which reads as "show". */
@Serializable
data class FeatureFlags(
    val projectMode: Boolean? = null,
    val googleTasks: Boolean? = null,
    val showListImages: Boolean = true,
)

/** One of `passkeys`. */
@Serializable
data class Passkey(val id: String = "", val name: String? = null, val createdAt: String? = null, val isSynced: Boolean = false)

@Serializable
data class Passkeys(val passkeys: List<Passkey> = emptyList())
