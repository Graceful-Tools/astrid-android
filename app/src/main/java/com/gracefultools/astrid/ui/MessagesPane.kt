package com.gracefultools.astrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme

/**
 * iOS iPadTaskManagerView.messagesPane: the selected list's messages pinned at the right of a wide
 * landscape window, under a toggle (sidebar.right, accent) that folds the pane to a 48dp rail.
 */
@Composable
fun MessagesPane(hidden: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, chat: @Composable () -> Unit) {
    val colors = AstridTheme.colors
    val label = stringResource(if (hidden) R.string.chat_show_messages_pane else R.string.chat_hide_messages)
    Column(modifier.fillMaxSize().background(colors.bgPrimary)) {
        Box(
            Modifier.statusBarsPadding().width(PaneLayout.RAIL).height(44.dp)
                .clickable(role = Role.Button, onClick = onToggle).semantics { contentDescription = label }
                .testTag("messages.togglePane"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.ViewSidebar, null, tint = colors.accent, modifier = Modifier.height(18.dp)) }
        if (!hidden) Box(Modifier.weight(1f)) { chat() }
    }
}
