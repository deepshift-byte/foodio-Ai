package com.sandpause.app.ui.child

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.sandpause.app.R
import com.sandpause.app.lock.ChildUi
import com.sandpause.app.ui.common.formatMmSs
import com.sandpause.app.ui.theme.ChildPalette
import com.sandpause.app.ui.theme.Nunito
import com.sandpause.core.hourglass.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val SUGGESTION_MS = 30_000L
private const val PARENT_PAD_HOLD_MS = 3_000L

/**
 * The child lock screen: an hourglass, the time left, the child's name, rotating
 * play suggestions and captions. It is pure UI, so the same content is used by
 * the lock activity and by the fallback overlay.
 */
@Composable
fun ChildLockContent(
    ui: ChildUi,
    fraction: () -> Float,
    onHourglassTap: () -> Unit,
    onEmergency: () -> Unit,
    onParentPad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topColor by animateColorAsState(moodTop(ui.mood), tween(700), label = "bg-top")
    val bottomColor by animateColorAsState(moodBottom(ui.mood), tween(700), label = "bg-bottom")
    val textColor by animateColorAsState(moodText(ui.mood), tween(700), label = "text")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockCorner(tint = textColor, onHeld = onParentPad)
                EmergencyButton(onClick = onEmergency)
            }

            Spacer(Modifier.weight(1f))

            Hourglass(
                mood = ui.mood,
                fraction = fraction,
                onTap = onHourglassTap,
                modifier = Modifier.height(300.dp),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = formatMmSs(ui.remainingMs),
                color = textColor,
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 60.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.child_resting_for, ui.childName),
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            SuggestionCard(ui.suggestions, ui.mood)

            Spacer(Modifier.height(12.dp))

            CaptionBubble(ui.caption)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LockCorner(tint: Color, onHeld: () -> Unit) {
    val description = stringResource(R.string.lock_corner_description)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.55f))
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(onHeld) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Finishing within the hold time is a no-op. Holding for the full time opens the parent PIN pad.
                    val released = withTimeoutOrNull(PARENT_PAD_HOLD_MS) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == null) onHeld()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun EmergencyButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ChildPalette.Emergency, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.child_emergency), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun SuggestionCard(custom: List<String>, mood: Mood) {
    val defaults = stringArrayResource(R.array.suggestions_default).toList()
    val items = custom.ifEmpty { defaults }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        while (true) {
            delay(SUGGESTION_MS)
            index = (index + 1) % items.size.coerceAtLeast(1)
        }
    }
    val shown = items.getOrElse(index % items.size.coerceAtLeast(1)) { "" }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
            label = "suggestion",
        ) { text ->
            Text(
                text = text,
                modifier = Modifier.padding(18.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = moodText(mood),
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun CaptionBubble(caption: String?) {
    // Keeps the last caption while the bubble fades out, so the text does not vanish mid-fade.
    var last by remember { mutableStateOf("") }
    LaunchedEffect(caption) {
        if (caption != null) last = caption
    }
    AnimatedVisibility(visible = caption != null, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = last,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = ChildPalette.YellowText,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun moodTop(mood: Mood): Color = when (mood) {
    Mood.RESTING -> ChildPalette.YellowTop
    Mood.IMPATIENT -> ChildPalette.RedTop
    Mood.FINISHED -> ChildPalette.GreenTop
}

private fun moodBottom(mood: Mood): Color = when (mood) {
    Mood.RESTING -> ChildPalette.YellowBottom
    Mood.IMPATIENT -> ChildPalette.RedBottom
    Mood.FINISHED -> ChildPalette.GreenBottom
}

private fun moodText(mood: Mood): Color = when (mood) {
    Mood.RESTING -> ChildPalette.YellowText
    Mood.IMPATIENT -> ChildPalette.RedText
    Mood.FINISHED -> ChildPalette.GreenText
}
