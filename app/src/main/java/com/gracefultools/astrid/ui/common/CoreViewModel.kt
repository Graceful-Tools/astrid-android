package com.gracefultools.astrid.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A view model that runs its core calls on [dispatcher]. The core does its work on its own pool, so
 * this only decides where the answer is handled; tests pass an unconfined one.
 */
abstract class CoreViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : ViewModel() {
    protected val scope: CoroutineScope get() = CoroutineScope(viewModelScope.coroutineContext + dispatcher)
    protected fun work(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
