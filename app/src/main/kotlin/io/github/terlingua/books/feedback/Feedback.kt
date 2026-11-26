package io.github.terlingua.books.feedback

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.terlingua.books.domain.Settings

/** Central visual + tactile + audible feedback. Visual ripples come from the Material 3 components. */
@Immutable
class Feedback internal constructor(
    private val player: FeedbackPlayer?,
    private val haptics: HapticFeedback?,
    private val soundEnabled: Boolean,
    private val hapticsEnabled: Boolean,
) {
    fun perform(event: FeedbackEvent) {
        if (hapticsEnabled) {
            haptics?.performHapticFeedback(
                when (event) {
                    FeedbackEvent.Tap -> HapticFeedbackType.VirtualKey
                    FeedbackEvent.Toggle -> HapticFeedbackType.ToggleOn
                    FeedbackEvent.Correct -> HapticFeedbackType.Confirm
                    FeedbackEvent.Incorrect -> HapticFeedbackType.Reject
                },
            )
        }
        if (soundEnabled) player?.play(event)
    }

    companion object {
        val None = Feedback(null, null, soundEnabled = false, hapticsEnabled = false)
    }
}

val LocalFeedback = staticCompositionLocalOf { Feedback.None }

@Composable
fun ProvideFeedback(player: FeedbackPlayer?, settings: Settings, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val feedback = remember(player, haptics, settings.sound, settings.haptics) {
        Feedback(player, haptics, settings.sound, settings.haptics)
    }
    CompositionLocalProvider(LocalFeedback provides feedback, content = content)
}

@Composable
fun rememberFeedbackClick(event: FeedbackEvent = FeedbackEvent.Tap, onClick: () -> Unit): () -> Unit {
    val feedback = LocalFeedback.current
    return remember(feedback, event, onClick) { { feedback.perform(event); onClick() } }
}

@Composable
fun FeedbackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    event: FeedbackEvent = FeedbackEvent.Tap,
    content: @Composable RowScope.() -> Unit,
) = Button(onClick = rememberFeedbackClick(event, onClick), modifier = modifier, enabled = enabled, content = content)

@Composable
fun FeedbackTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    event: FeedbackEvent = FeedbackEvent.Tap,
    content: @Composable RowScope.() -> Unit,
) = FilledTonalButton(onClick = rememberFeedbackClick(event, onClick), modifier = modifier, enabled = enabled, content = content)

@Composable
fun FeedbackOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    event: FeedbackEvent = FeedbackEvent.Tap,
    content: @Composable RowScope.() -> Unit,
) = OutlinedButton(onClick = rememberFeedbackClick(event, onClick), modifier = modifier, enabled = enabled, content = content)

@Composable
fun FeedbackTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    event: FeedbackEvent = FeedbackEvent.Tap,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) = TextButton(
    onClick = rememberFeedbackClick(event, onClick),
    modifier = modifier,
    enabled = enabled,
    contentPadding = contentPadding,
    content = content,
)

@Composable
fun FeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    event: FeedbackEvent = FeedbackEvent.Tap,
    content: @Composable () -> Unit,
) = IconButton(onClick = rememberFeedbackClick(event, onClick), modifier = modifier, enabled = enabled, content = content)
