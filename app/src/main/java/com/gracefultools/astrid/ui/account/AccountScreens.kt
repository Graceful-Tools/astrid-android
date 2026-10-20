package com.gracefultools.astrid.ui.account

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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

/** iOS's profile numbers: completed, inspired, supported — and data export. */
@Composable
fun ProfileScreen(vm: AccountViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadProfile() }
    LaunchedEffect(state.exported) {
        val path = state.exported ?: return@LaunchedEffect
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(path))
        val send = Intent(Intent.ACTION_SEND).setType(if (path.endsWith(".csv")) "text/csv" else "application/json")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
        vm.exportShared()
    }
    Page(stringResource(R.string.profile_title), vm, onBack) {
        state.stats?.let { stats ->
            listOf(
                Triple(R.string.stats_completed, R.string.stats_completed_tagline, stats.completed),
                Triple(R.string.stats_inspired, R.string.stats_inspired_tagline, stats.inspired),
                Triple(R.string.stats_supported, R.string.stats_supported_tagline, stats.supported),
            ).forEach { (label, tagline, value) ->
                Card {
                    Text(value.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Text(stringResource(label), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Text(stringResource(tagline), fontSize = 15.sp, color = colors.textSecondary)
                }
            }
        }
        Text(stringResource(R.string.profile_export).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
        Card {
            Row {
                for (format in listOf("json", "csv")) {
                    TextButton(onClick = {
                        val dir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
                        vm.export(format, java.io.File(dir, "astrid-export.$format").absolutePath)
                    }) { Text(format.uppercase()) }
                }
            }
        }
    }
}
