package com.gracefultools.astrid.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.markdown.MarkdownView
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing

@Composable
fun ChatScreen(vm: ChatViewModel, title: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    val list = rememberLazyListState()
    BackHandler(onBack = onBack)
    LaunchedEffect(state.chat.messages.size) { if (state.chat.messages.isNotEmpty()) list.animateScrollToItem(state.chat.messages.lastIndex) }
    Column(Modifier.fillMaxSize().background(colors.bgPrimary).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back), tint = colors.textPrimary) }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }
        if (state.chat.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chat_empty), color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                state = list, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(Spacing.s12), verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            ) {
                items(state.chat.messages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (message.isMine) colors.accent.copy(alpha = 0.16f) else if (colors.isDark) colors.bgSecondary else Color.White)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            if (!message.isMine) Text(message.authorName ?: message.initials, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                            if (message.blocks.isNotEmpty()) MarkdownView(message.blocks) else Text(message.content, color = colors.textPrimary, fontSize = 16.sp)
                            if (message.isPending) Text(stringResource(R.string.tasks_waiting_to_send), fontSize = 12.sp, color = colors.textMuted)
                        }
                    }
                }
            }
        }
        var text by rememberSaveable { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().background(colors.headerBg).navigationBarsPadding().imePadding().padding(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text, onValueChange = { text = it }, placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                shape = RoundedCornerShape(12.dp), maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.inputBg, unfocusedContainerColor = colors.inputBg,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.send(text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.detail_send), tint = colors.accent)
            }
        }
    }
}
