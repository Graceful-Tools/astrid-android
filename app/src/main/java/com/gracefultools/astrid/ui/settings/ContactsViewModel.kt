package com.gracefultools.astrid.ui.settings

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.platform.DeviceContact
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant

data class ContactsState(
    /** Synced Contacts: the server's count, null until it answers. */
    val count: Int? = null,
    val syncing: Boolean = false,
    /** When this device last uploaded, this session (iOS keeps it in memory too). */
    val lastSync: Instant? = null,
)

/**
 * iOS's Settings → Contacts (AATD-68): the count, and Sync Contacts Now — the address book read by
 * [readBook] (an Android adapter) and handed to the core's `uploadContacts`, which batches it.
 */
class ContactsViewModel(
    private val core: Core,
    private val readBook: suspend () -> List<DeviceContact>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    fun loadCount() = work {
        val total = attempt { total(core.run(command("contacts"))) } ?: return@work
        _state.update { it.copy(count = total) }
    }

    fun sync() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        work {
            val book = readBook().map { mapOf("email" to it.email, "name" to it.name, "phoneNumber" to it.phoneNumber) }
            val total = attempt { total(core.run(command("uploadContacts", "contacts" to book))) }
            _state.update { s -> if (total != null) s.copy(syncing = false, count = total, lastSync = Instant.now()) else s.copy(syncing = false) }
        }
    }

    private fun total(answer: kotlinx.serialization.json.JsonElement): Int? =
        ((answer as? JsonObject)?.get("total") as? JsonPrimitive)?.intOrNull

    /** Offline or refused: the section keeps what it showed. iOS's sync fails silently the same way. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (_: CoreFailure) { null }
}
