package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors

/**
 * Professional Text Renderer for Messages.
 * Supports Markdown parsing, spoilers, and BiDi (RTL/LTR) detection.
 */
@Composable
fun TextRenderer(
    text: String,
    modifier: Modifier = Modifier,
    isSpoiler: Boolean = false
) {
    var revealed by remember { mutableStateOf(!isSpoiler) }
    
    val annotatedString = remember(text) {
        parseMarkdown(text)
    }

    val isRtl = remember(text) { text.isRtl() }

    Box(
        modifier = modifier
            .clickable(enabled = isSpoiler && !revealed) { revealed = true }
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = if (isSpoiler && !revealed) Color.Transparent else MaterialTheme.colorScheme.onSurface,
                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                ),
                modifier = if (isSpoiler && !revealed) {
                    Modifier
                        .padding(4.dp)
                        .drawSpoilerOverlay()
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 * Extension to detect if a string is RTL (Persian/Arabic).
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

/**
 * Simple Markdown Parser.
 * Supports: **bold**, *italic*, `code`, and [links].
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        
        val regex = Regex("(\\n|\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(text)

        matches.forEach { match ->
            val start = match.range.first
            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val groupValue = match.value
            when {
                groupValue.startsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(groupValue.removeSurrounding("**"))
                    }
                }
                groupValue.startsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(groupValue.removeSurrounding("*"))
                    }
                }
                groupValue.startsWith("`") -> {
                    withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = Color.Black.copy(alpha = 0.2f))) {
                        append(groupValue.removeSurrounding("`"))
                    }
                }
                groupValue.startsWith("[") -> {
                    val label = groupValue.substringAfter("[").substringBefore("]")
                    val url = groupValue.substringAfter("(").substringBefore(")")
                    withStyle(SpanStyle(color = MatrixColors.AccentBlue, textDecoration = TextDecoration.Underline)) {
                        pushStringAnnotation(tag = "URL", annotation = url)
                        append(label)
                        pop()
                    }
                }
                else -> append(groupValue)
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

fun Modifier.drawSpoilerOverlay(): Modifier = this // Placeholder
