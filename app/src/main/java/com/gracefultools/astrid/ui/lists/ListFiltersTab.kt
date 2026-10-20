package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles

/** iOS's `.yellow`, the favourite star's colour. */
private val FavoriteYellow = Color(0xFFFFCC00)

/**
 * iOS ListSortFiltersTab. A placeholder for now: only its Favorite section, so the toggle has a
 * home while the sort and filter sections are built (the lead's branch fills this in). [listId] is
 * the list whose filters these will be.
 */
@Composable
fun ListFiltersTab(listId: String, isFavorite: Boolean, onFavoriteChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = AstridTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s24),
    ) {
        FormSection(footer = if (isFavorite) stringResource(R.string.lists_favorite_description) else null) {
            FormRow(Modifier.toggleable(value = isFavorite, role = Role.Switch, onValueChange = onFavoriteChange)) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
                    tint = if (isFavorite) FavoriteYellow else colors.textMuted, modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.lists_favorite), style = TextStyles.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Switch(checked = isFavorite, onCheckedChange = null)
            }
        }
    }
}
