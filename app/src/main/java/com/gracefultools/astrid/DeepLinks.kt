package com.gracefultools.astrid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the app was opened with, held until the signed-in screens are ready to take it. */
sealed interface Arrival {
    /** `astrid.cc/t/AATD-7`, `astrid://task/<id>`: open that task. */
    data class OpenTask(val ref: String) : Arrival
    /** Text shared from another app (iOS's share extension), or the "New task" shortcut (empty). */
    data class NewTask(val text: String) : Arrival
}

class DeepLinks {
    private val _pending = MutableStateFlow<Arrival?>(null)
    val pending: StateFlow<Arrival?> = _pending.asStateFlow()
    fun offer(arrival: Arrival) { _pending.value = arrival }
    fun take(): Arrival? = _pending.value.also { _pending.value = null }

    companion object {
        /** A link the app knows, or null. */
        fun parse(link: String): Arrival? {
            if (link == "astrid://new") return Arrival.NewTask("")
            Regex("""^https://(?:www\.)?astrid\.cc/t/([A-Za-z0-9-]+)""").find(link)?.let { return Arrival.OpenTask(it.groupValues[1]) }
            Regex("""^astrid://task/([A-Za-z0-9_-]+)""").find(link)?.let { return Arrival.OpenTask(it.groupValues[1]) }
            return null
        }

        /** What a share carries, as one line for the quick-add box: subject, then text (often a URL). */
        fun shared(subject: String?, text: String?): String =
            listOfNotNull(subject?.trim()?.takeIf { it.isNotEmpty() }, text?.trim()?.takeIf { it.isNotEmpty() })
                .distinct().joinToString(" ")
    }
}
