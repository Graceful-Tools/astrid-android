package com.gracefultools.astrid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A link the app was opened with, held until the signed-in screens are ready to take it. */
class DeepLinks {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()
    fun offer(url: String) { _pending.value = url }
    fun take(): String? = _pending.value.also { _pending.value = null }
}
