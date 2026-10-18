package com.gracefultools.astrid.ui.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing

/** The iOS LoginView: wordmark, tagline, and one button per provider the hand-off page can start. */
@Composable
fun SignInScreen(
    state: SignInState,
    onProvider: (String?) -> Unit,
    onOpenAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.bgPrimary).safeDrawingPadding().padding(Spacing.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.auth_tagline), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
        Spacer(Modifier.height(Spacing.s40))
        when (state) {
            SignInState.Checking, SignInState.Completing, SignInState.SignedIn -> CircularProgressIndicator(color = colors.accent)
            SignInState.WaitingForBrowser -> {
                Text(stringResource(R.string.auth_waiting_for_browser), color = colors.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.s16))
                ProviderButton(R.string.auth_open_browser_again, onClick = onOpenAgain)
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = colors.textPrimary) }
            }
            is SignInState.SignedOut -> {
                Text(
                    stringResource(R.string.auth_sign_in_header),
                    style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(Spacing.s8))
                    Text(stringResource(R.string.auth_browser_sign_in_failed), color = com.gracefultools.astrid.ui.theme.Error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(Spacing.s20))
                ProviderButton(R.string.auth_continue_with_google, Color.White, colors.textPrimary) { onProvider("google") }
                ProviderButton(R.string.auth_continue_with_passkey) { onProvider(null) }
                ProviderButton(R.string.auth_continue_with_github, Color(0xFF24292F), Color.White) { onProvider("github") }
                ProviderButton(R.string.auth_continue_with_sso, colors.buttonBg, colors.textPrimary) { onProvider("sso") }
            }
        }
    }
}

@Composable
private fun ProviderButton(
    @StringRes title: Int,
    background: Color = AstridTheme.colors.accent,
    foreground: Color = AstridTheme.colors.accentText,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(vertical = Spacing.s4).height(52.dp),
        shape = RoundedCornerShape(Radius.large),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
    ) { Text(stringResource(title), style = MaterialTheme.typography.labelLarge) }
}
