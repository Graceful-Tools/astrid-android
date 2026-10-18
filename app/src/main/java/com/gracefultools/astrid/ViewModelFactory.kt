package com.gracefultools.astrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** A factory from a lambda — the graph is hand-wired, so each screen says how its model is made. */
inline fun <reified T : ViewModel> factory(crossinline make: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
    }
