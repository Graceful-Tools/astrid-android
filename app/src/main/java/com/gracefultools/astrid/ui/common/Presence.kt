package com.gracefultools.astrid.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS's NetworkMonitor.isConnected, for drawing an offline cue: true while the default network
 * reaches the internet. UI state only — the core decides what offline means for a write.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val online = remember { mutableStateOf(inspecting || isOnlineNow(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            override fun onLost(network: Network) { online.value = false }
            override fun onAvailable(network: Network) { online.value = true }
        }
        val registered = runCatching { manager?.registerDefaultNetworkCallback(callback) }.isSuccess && manager != null
        onDispose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun isOnlineNow(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)

/** iOS's chat offline strip: wifi.slash and "Offline — messages will sync when connected". */
@Composable
fun OfflineStrip(modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    Row(
        modifier.fillMaxWidth().background(colors.bgSecondary).padding(horizontal = Spacing.s12, vertical = Spacing.s4)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WifiOff, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.chat_offline), style = TextStyles.caption2, color = colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** iOS's AgentTypingIndicator: three bouncing dots and "<agent> is thinking...". */
@Composable
fun AgentTypingIndicator(agentName: String?, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    val name = agentName ?: stringResource(R.string.chat_agent_fallback)
    val bounce = rememberInfiniteTransition(label = "typing")
    Row(
        modifier.padding(horizontal = Spacing.s12, vertical = Spacing.s8).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            for (index in 0 until 3) {
                val lift by bounce.animateFloat(
                    initialValue = 0f, targetValue = -4f,
                    animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse, StartOffset(index * 150)),
                    label = "dot$index",
                )
                Box(Modifier.offset(y = lift.dp).size(6.dp).clip(CircleShape).background(colors.textMuted))
            }
        }
        Text(stringResource(R.string.chat_agent_thinking, name), style = TextStyles.caption2, color = colors.textMuted)
    }
}
