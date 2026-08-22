package com.matrixmessenger.feature.chatlist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.components.MatrixIconButton
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography

@Composable
fun ChatListHeader(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MatrixDimens.HeaderHeight)
            .background(MatrixColors.DarkSurface)
            .padding(horizontal = MatrixDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixIconButton(
            icon = MatrixIcons.MoreVert,
            onClick = onMenuClick,
            tint = MatrixColors.DarkTextSecondary
        )
        
        Text(
            text = "Messenger",
            style = MatrixTypography.HeaderTitle,
            color = MatrixColors.DarkTextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = MatrixDimens.SpacingSmall)
        )
        
        MatrixIconButton(
            icon = MatrixIcons.Search,
            onClick = onSearchClick,
            tint = MatrixColors.DarkTextSecondary
        )
    }
}

@Composable
fun ChatFilterRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = filters.indexOf(selectedFilter),
        containerColor = MatrixColors.DarkSurface,
        contentColor = MatrixColors.Accent,
        edgePadding = MatrixDimens.SpacingMedium,
        indicator = { tabPositions ->
            if (filters.isNotEmpty()) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[filters.indexOf(selectedFilter)]),
                    color = MatrixColors.Accent
                )
            }
        },
        divider = {},
        modifier = modifier.fillMaxWidth()
    ) {
        filters.forEach { filter ->
            Tab(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                text = {
                    Text(
                        text = filter,
                        style = MatrixTypography.ChatItemMeta,
                        color = if (filter == selectedFilter) MatrixColors.Accent else MatrixColors.DarkTextSecondary
                    )
                }
            )
        }
    }
}
