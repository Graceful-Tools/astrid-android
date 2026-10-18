package com.gracefultools.astrid.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.BuildConfig
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Error
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    BackHandler(onBack = onBack)
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    Column(Modifier.fillMaxSize().background(colors.bgPrimary).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back), tint = colors.textPrimary) }
            Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
            Group(stringResource(R.string.settings_account)) {
                Text(state.user?.name ?: "", fontSize = 17.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(state.user?.email ?: "", fontSize = 15.sp, color = colors.textSecondary)
                Text(
                    if (state.waitingToSend > 0) stringResource(R.string.settings_waiting_to_send, state.waitingToSend) else stringResource(R.string.settings_all_sent),
                    fontSize = 13.sp, color = colors.textMuted,
                )
            }
            Group(stringResource(R.string.settings_appearance)) {
                listOf(
                    ThemeMode.OCEAN to R.string.settings_theme_ocean, ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark, ThemeMode.AUTO to R.string.settings_theme_auto,
                ).forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { vm.setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == mode, onClick = { vm.setTheme(mode) })
                        Text(stringResource(label), color = colors.textPrimary, fontSize = 17.sp)
                    }
                }
            }
            Group(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, vm.coreVersion), color = colors.textSecondary, fontSize = 15.sp)
            }
            Text(
                stringResource(R.string.settings_sign_out), color = Error, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (colors.isDark) colors.bgSecondary else Color.White)
                    .clickable(onClick = vm::signOut).padding(Spacing.s16),
            )
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (colors.isDark) colors.bgSecondary else Color.White)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp)).padding(Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) { content() }
}
