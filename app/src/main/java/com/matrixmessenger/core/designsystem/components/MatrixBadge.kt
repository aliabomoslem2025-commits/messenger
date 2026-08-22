package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

@Composable
fun MatrixBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MatrixColors.Accent,
    contentColor: Color = MatrixColors.White,
    isMuted: Boolean = false
) {
    if (count <= 0) return

    val bg = if (isMuted) MatrixColors.DarkTextSecondary.copy(alpha = 0.5f) else backgroundColor
    
    Box(
        modifier = modifier
            .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
            .background(bg, MatrixShapes.Badge)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
