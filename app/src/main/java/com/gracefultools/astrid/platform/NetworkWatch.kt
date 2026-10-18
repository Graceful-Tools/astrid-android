package com.gracefultools.astrid.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * iOS's NWPathMonitor: when the network comes back, tell the core, which drains the Outbox and
 * reconnects the live stream now rather than on its next timer.
 */
object NetworkWatch {
    fun start(context: Context, core: Core, scope: CoroutineScope) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            private var wasOnline = true
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (online && !wasOnline) scope.launch { runCatching { core.run(command("networkRestored")) } }
                wasOnline = online
            }
            override fun onLost(network: Network) { wasOnline = false }
        })
    }
}
