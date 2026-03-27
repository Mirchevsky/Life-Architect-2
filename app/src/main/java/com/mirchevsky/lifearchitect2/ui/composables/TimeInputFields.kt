package com.mirchevsky.lifearchitect2.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimeInputFields(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    fieldBackground: Color,
    fieldContent: Color,
    modifier: Modifier = Modifier
) {
    val selectionColors = TextSelectionColors(
        handleColor = fieldContent,
        backgroundColor = fieldContent.copy(alpha = 0.3f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.width(272.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeDigitField(
                    value = hour,
                    range = 0..23,
                    onValueChange = onHourChange,
                    fieldBackground = fieldBackground,
                    fieldContent = fieldContent
                )

                Box(
                    modifier = Modifier.width(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ":",
                        color = fieldContent,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(0.96f)
                    )
                }

                TimeDigitField(
                    value = minute,
                    range = 0..59,
                    onValueChange = onMinuteChange,
                    fieldBackground = fieldBackground,
                    fieldContent = fieldContent
                )
            }
        }
    }
}

@Composable
private fun TimeDigitField(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    fieldBackground: Color,
    fieldContent: Color
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value.toTwoDigits(), TextRange(2)))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            val normalized = value.toTwoDigits()
            if (textFieldValue.text != normalized) {
                textFieldValue = TextFieldValue(
                    normalized,
                    TextRange(normalized.length)
                )
            }
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { incoming ->
            val isValidEdit =
                incoming.text.length <= 2 && incoming.text.all(Char::isDigit)

            if (isValidEdit) {
                textFieldValue = incoming
                val parsed = incoming.text
                    .toIntOrNull()
                    ?.coerceIn(range.first, range.last)
                    ?: 0
                onValueChange(parsed)
            }
        },
        textStyle = TextStyle(
            color = fieldContent,
            fontSize = 50.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 50.sp,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .width(120.dp)
            .height(82.dp)
            .background(fieldBackground, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                fieldContent.copy(alpha = 0.22f),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused) {
                    val normalized = (textFieldValue.text.toIntOrNull() ?: 0)
                        .coerceIn(range.first, range.last)
                    val normalizedText = normalized.toTwoDigits()
                    textFieldValue = TextFieldValue(
                        normalizedText,
                        TextRange(normalizedText.length)
                    )
                    onValueChange(normalized)
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        cursorBrush = SolidColor(fieldContent),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                innerTextField()
            }
        }
    )
}

private fun Int.toTwoDigits(): String =
    coerceAtLeast(0).toString().padStart(2, '0')