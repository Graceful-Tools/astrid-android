package com.gracefultools.astrid.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing

@Composable
private fun Page(title: String, vm: AccountViewModel, onBack: () -> Unit, content: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    Box(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back), tint = colors.textPrimary) }
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) { content() }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (colors.isDark) colors.bgSecondary else Color.White)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp)).padding(Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) { content() }
}

/** Agent Hub: who does work here and how each runs — the core's modes, a locked agent left alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsScreen(vm: AccountViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(Unit) { vm.loadAgents() }
    Page(stringResource(R.string.agents_title), vm, onBack) {
        state.agents?.agents?.forEach { agent ->
            Card {
                Text(agent.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                agent.description?.let { Text(it, fontSize = 15.sp, color = colors.textSecondary) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    agent.modes.forEach { mode ->
                        val chosen = mode == agent.mode
                        Text(
                            agentModeLabel(mode), fontSize = 14.sp, color = if (chosen) colors.accentText else colors.textPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (chosen) colors.accent else colors.bgTertiary)
                                .clickable(enabled = !agent.locked) { vm.setAgentMode(agent.id, mode) }
                                .semantics { selected = chosen }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun agentModeLabel(mode: String): String = stringResource(
    when (mode) {
        "api" -> R.string.agents_mode_api
        "polling" -> R.string.agents_mode_polling
        "webhook" -> R.string.agents_mode_webhook
        else -> R.string.agents_mode_off
    },
)

/** Connections: everything that can act as this account, each revocable one with Revoke. */
@Composable
fun ConnectionsScreen(vm: AccountViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    LaunchedEffect(Unit) { vm.loadConnections() }
    Page(stringResource(R.string.connections_title), vm, onBack) {
        state.connections?.connections?.forEach { row ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        listOfNotNull(row.owner, row.actsAs, row.lastUsedAt?.take(10)).joinToString(" · ").takeIf { it.isNotEmpty() }
                            ?.let { Text(it, fontSize = 14.sp, color = colors.textSecondary) }
                    }
                    if (row.revocable) TextButton(onClick = { vm.revoke(row.kind, row.id) }) { Text(stringResource(R.string.connections_revoke), color = Error) }
                }
            }
        }
    }
}
