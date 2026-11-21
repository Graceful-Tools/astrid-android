package com.gracefultools.astrid.model

import kotlinx.serialization.Serializable

/**
 * A project's goals and guardrails — the core's `ProjectGoals` (`getProjectGoals`, `getListGoals`,
 * `setProjectGoals`; astrid-core ADEV-187, web ADEV-179). Both lists empty means nothing is stated
 * and nothing is drawn; `canEdit` false means read-only.
 */
@Serializable
data class ProjectGoals(
    val listId: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val goals: List<Goal> = emptyList(),
    val guardrails: List<Guardrail> = emptyList(),
    val canEdit: Boolean = false,
) {
    val isEmpty: Boolean get() = goals.isEmpty() && guardrails.isEmpty()
}

@Serializable
data class Goal(
    val id: String = "",
    val statement: String = "",
    val measure: String? = null,
    val target: String? = null,
    val current: String? = null,
)

/** `kind` is `stated` or `enforced`; a word this build does not know reads as stated, as in the core. */
@Serializable
data class Guardrail(val id: String = "", val statement: String = "", val kind: String = "stated") {
    val isEnforced: Boolean get() = kind == "enforced"
}
