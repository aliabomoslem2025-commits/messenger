package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography

@Composable
fun TextMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    val isRtl = remember(text) { text.isRtl() }
    
    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Text(
            text = text,
            style = MatrixTypography.MessageBody,
            color = MatrixColors.DarkTextPrimary,
            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
            modifier = modifier
        )
    }
}

/**
 * Extension to detect if a string is RTL (Persian/Arabic/Hebrew).
 */
fun String.isRtl(): Boolean {
    for (char in this) {
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
    }
    return false
}
