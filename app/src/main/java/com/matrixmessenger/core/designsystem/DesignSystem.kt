package com.matrixmessenger.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

@Composable
fun MessengerSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MatrixShapes.CardMedium,
    color: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        tonalElevation = tonalElevation,
        content = content
    )
}

@Composable
fun MessengerCard(
    modifier: Modifier = Modifier,
    shape: Shape = MatrixShapes.CardLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content
    )
}
