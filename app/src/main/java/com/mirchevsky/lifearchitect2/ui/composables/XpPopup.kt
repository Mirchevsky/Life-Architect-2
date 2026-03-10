package com.mirchevsky.lifearchitect2.ui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.mirchevsky.lifearchitect2.ui.theme.BrandAmber
import com.mirchevsky.lifearchitect2.ui.theme.BrandGreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An animated overlay composable that displays XP gained or lost.
 *
 * On a critical hit, the text is larger and rendered in bright gold instead of green.
 * No "CRITICAL HIT" label is shown — the visual emphasis speaks for itself.
 *
 * @param amount The XP amount. Positive for gain, negative for loss.
 * @param isCritical True if the gain was a critical hit.
 * @param onDismiss Called when the animation completes.
 * @param modifier Modifier for positioning the pop-up within its parent.
 */
@Composable
fun XpPopup(
    amount: Int,
    isCritical: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val yOffset = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        yOffset.animateTo(
            targetValue = -120f,
            animationSpec = tween(durationMillis = 1500)
        )
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400)
        )
        onDismiss()
    }

    val text = if (amount >= 0) "+${amount} XP" else "${amount} XP"

    val color = when {
        isCritical -> BrandAmber // Gold/amber — visually distinct from normal green
        amount >= 0 -> BrandGreen // Green
        else -> MaterialTheme.colorScheme.error // Red for loss
    }

    val fontSize = if (isCritical) 30.sp else 22.sp

    Box(
        modifier = modifier
            .offset(y = yOffset.value.dp)
            .alpha(alpha.value)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}
