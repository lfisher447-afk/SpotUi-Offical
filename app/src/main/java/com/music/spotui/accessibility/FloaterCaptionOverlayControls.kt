package com.music.spotui.accessibility

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.translator.CaptionState
import com.music.spotui.translator.TranslationConfigState
import java.util.Locale

/**
 * Unified non-intrusive caption overlay host:
 * - When internal MediaProjection game capture is running and [TranslationConfigState.clickThroughLocked] is true,
 *   renders a minimal click-through caption pill (touches pass straight through to the game) with smooth
 *   4-second silence auto-hide via [CaptionState.captionVisible].
 * - Otherwise delegates to interactive [TranslationBubbleOverlayContent] for drag/resize/text-size tuning.
 */
@Composable
internal fun GameLiveCaptionHost(
    captionState: CaptionState,
    config: TranslationConfigState,
    bubble: TranslationBubbleState,
    widthDp: Float,
    textSizeSp: Float,
    opacity: Float,
    gameLabel: String?,
    onBubbleDrag: (Float, Float) -> Unit,
    onBubbleDragEnd: () -> Unit,
    onBubbleResizeWidthPx: (Float) -> Unit,
    onBubbleTextSizeDelta: (Float) -> Unit,
    onDismissBubble: () -> Unit,
    onOpenTranslatorTab: () -> Unit,
) {
    if (captionState.captureActive && config.clickThroughLocked) {
        val visible = captionState.captionVisible &&
            (captionState.translatedText.isNotBlank() || captionState.originalText.isNotBlank())
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
        ) {
            ClickThroughLiveCaptionPill(
                captionState = captionState,
                showDualLine = config.showDualLineCaption,
                widthDp = widthDp,
                textSizeSp = textSizeSp,
                opacity = opacity,
                gameLabel = gameLabel,
            )
        }
    } else {
        TranslationBubbleOverlayContent(
            bubble = if (captionState.captureActive && captionState.translatedText.isNotBlank()) {
                bubble.copy(
                    originalText = if (config.showDualLineCaption) captionState.originalText else "",
                    translatedEnglish = captionState.translatedText,
                    detectedLangCode = captionState.detectedLangCode,
                    targetLangCode = captionState.targetLangCode,
                    isListening = true,
                    statusHint = captionState.statusMessage,
                )
            } else {
                bubble
            },
            widthDp = widthDp,
            textSizeSp = textSizeSp,
            opacity = opacity,
            gameLabel = gameLabel,
            onBubbleDrag = onBubbleDrag,
            onBubbleDragEnd = onBubbleDragEnd,
            onBubbleResizeWidthPx = onBubbleResizeWidthPx,
            onBubbleTextSizeDelta = onBubbleTextSizeDelta,
            onDismissBubble = onDismissBubble,
            onOpenTranslatorTab = onOpenTranslatorTab,
        )
    }
}

@Composable
internal fun ClickThroughLiveCaptionPill(
    captionState: CaptionState,
    showDualLine: Boolean,
    widthDp: Float,
    textSizeSp: Float,
    opacity: Float,
    gameLabel: String?,
) {
    val cs = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        targetValue = if (captionState.rmsLevel > 0.08f) cs.primary else cs.outlineVariant,
        label = "clickThroughCaptionBorder",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0C1018).copy(alpha = opacity.coerceIn(0.60f, 0.96f)),
        border = BorderStroke(1.25.dp, borderColor.copy(alpha = 0.85f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .width(widthDp.coerceIn(180f, 420f).dp)
            .testTag("click_through_live_caption_pill"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954)),
                )
                Text(
                    text = "${captionState.detectedLangCode.uppercase(Locale.US)} → ${captionState.targetLangCode.uppercase(Locale.US)}",
                    color = Color(0xFF81C784),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Surface(
                    color = Color(0xFF1F2838),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "${captionState.engineUsed.badge} · ${captionState.latencyMs}ms",
                        color = Color(0xFF8AB4F8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = gameLabel ?: "PASS-THROUGH",
                    color = Color(0xFF9EA8BC),
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Click-through touch pass-through locked",
                    tint = Color(0xFF9EA8BC),
                    modifier = Modifier.size(11.dp),
                )
            }

            if (showDualLine && captionState.originalText.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color(0xFF9EA8BC),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = captionState.originalText,
                        color = Color(0xFFB7C0D1),
                        fontSize = (textSizeSp - 2.5f).coerceAtLeast(9.5f).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = captionState.translatedText.ifBlank { captionState.statusMessage },
                color = Color.White,
                fontSize = textSizeSp.sp,
                lineHeight = (textSizeSp + 4f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
