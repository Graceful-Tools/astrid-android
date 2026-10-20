package com.gracefultools.astrid.ui.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.Accent
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.DarkColors
import com.gracefultools.astrid.ui.theme.LightColors
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/**
 * iOS LoginView: the Astrid character beside the wordmark and slogan, "Sign in to get started!",
 * and one button per provider the deployment offers, in its order. The page is Light's white (or
 * Dark's), never Ocean's cyan, because on iOS it covers the app background (AATD-58).
 */
@Composable
fun SignInScreen(
    state: SignInState,
    look: SignInLook,
    onProvider: (String?) -> Unit,
    onOpenAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = if (AstridTheme.colors.isDark) DarkColors else LightColors
    Column(
        Modifier.fillMaxSize().background(palette.bgPrimary).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(Spacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s16),
    ) {
        Spacer(Modifier.height(Spacing.s16))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s16), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.astrid_character), null,
                Modifier.size(88.dp).clip(RoundedCornerShape(Radius.xLarge)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text(
                    look.wordmark ?: stringResource(R.string.app_name).lowercase(),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(look.slogan ?: stringResource(R.string.auth_tagline), fontSize = 16.sp, color = palette.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.s12))
        val buttons = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = Spacing.s32)
        when (state) {
            SignInState.Checking, SignInState.Completing, SignInState.SignedIn -> CircularProgressIndicator(color = Accent)
            SignInState.WaitingForBrowser -> {
                Text(stringResource(R.string.auth_waiting_for_browser), style = TextStyles.body, color = palette.textPrimary, textAlign = TextAlign.Center)
                Column(buttons, verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    ProviderButton(R.string.auth_open_browser_again, Icons.Filled.OpenInBrowser, Accent, Color.White, onOpenAgain)
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = palette.textPrimary) }
            }
            is SignInState.SignedOut -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s32), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s16)) {
                    Text(stringResource(R.string.auth_sign_in_header), style = TextStyles.headline, color = palette.textPrimary)
                    if (state.error != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Error, null, tint = com.gracefultools.astrid.ui.theme.Error, modifier = Modifier.size(14.dp))
                            Text(state.error, style = TextStyles.caption, color = com.gracefultools.astrid.ui.theme.Error)
                        }
                    }
                }
                Column(buttons, verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    // iOS's neutral: white 0.95 on light, white 0.2 on dark.
                    val neutral = if (palette.isDark) Color(0.2f, 0.2f, 0.2f) else Color(0.95f, 0.95f, 0.95f)
                    val onNeutral = if (palette.isDark) Color.White else Color.Black
                    for (provider in look.providers) when (provider) {
                        "google" -> ProviderButton(R.string.auth_continue_with_google, Icons.Filled.Language, Accent, Color.White) { onProvider("google") }
                        // The hand-off page offers every way in; a passkey is one of them.
                        "passkey" -> ProviderButton(R.string.auth_continue_with_passkey, Icons.Filled.Key, neutral, onNeutral) { onProvider(null) }
                        "github" -> ProviderButton(R.string.auth_continue_with_github, Icons.Filled.Code, neutral, onNeutral) { onProvider("github") }
                        "sso" -> ProviderButton(R.string.auth_continue_with_sso, Icons.Filled.Business, neutral, onNeutral) { onProvider("sso") }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.s24))
    }
}

/** iOS providerButton: icon and title, 50 tall, radius 12, headline type. */
@Composable
private fun ProviderButton(
    @StringRes title: Int,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(Radius.large),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.s16),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.size(Spacing.s12))
        Text(stringResource(title), style = TextStyles.headline)
    }
}
