package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    backgroundColor: Color = MatrixColors.DarkSurface,
    contentColor: Color = MatrixColors.DarkTextPrimary
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MatrixTypography.HeaderTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MatrixTypography.HeaderSubtitle,
                        color = MatrixColors.DarkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationIcon != null) {
                navigationIcon()
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor
        ),
        modifier = modifier.height(MatrixDimens.HeaderHeight)
    )
}
