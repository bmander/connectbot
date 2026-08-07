/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.connectbot.R
import org.connectbot.service.ConnectionOutcome
import org.connectbot.service.ConnectionProgress
import org.connectbot.service.ConnectionStage

/** Wait this long before showing an elapsed counter, so quick connects stay quiet. */
private const val ELAPSED_VISIBLE_AFTER_MILLIS = 3_000L

/** How long the all-ticks state is held before the overlay fades out. */
private const val SUCCESS_DWELL_MILLIS = 300L

/** Status symbol column. */
private val SYMBOL_COLUMN_WIDTH = 24.dp

/** Elapsed time column, wide enough for three digits of seconds. */
private val TIMER_COLUMN_WIDTH = 44.dp

/**
 * Card width.
 *
 * The three columns are laid out against a known width rather than sized to their
 * content, so the labels keep a common left edge and the timer a common right edge
 * as stages change beneath them.
 */
private val CARD_WIDTH = 290.dp

const val TAG_CONNECTION_PROGRESS_OVERLAY = "connection_progress_overlay"
const val TAG_CONNECTION_PROGRESS_CANCEL = "connection_progress_cancel"

/**
 * Shows which phase of connecting is underway, and offers a way out of one that is
 * stuck.
 *
 * Entirely state-hoisted: it takes a snapshot and a callback, so it previews and
 * tests without a TerminalBridge behind it.
 */
@Composable
fun ConnectionProgressOverlay(
    progress: ConnectionProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold the overlay up briefly on success so the last tick is actually seen,
    // rather than the whole thing vanishing the instant the shell attaches.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(progress?.outcome, progress == null) {
        visible = when {
            progress == null -> false

            progress.outcome is ConnectionOutcome.Succeeded -> {
                delay(SUCCESS_DWELL_MILLIS)
                false
            }

            else -> true
        }
    }

    AnimatedVisibility(
        visible = visible && progress != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(400)),
        modifier = modifier,
    ) {
        val snapshot = progress ?: return@AnimatedVisibility

        // A framed, opaque card rather than the translucent scrim the reconnect
        // overlay uses. That one sits flush against the bottom edge, but this floats
        // over the middle of live terminal output, where a half-transparent panel
        // leaves the text underneath competing with the stage list. An outlined
        // surface reads as a distinct object no matter what colour scheme the
        // terminal is running.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
            modifier = Modifier
                .padding(24.dp)
                .testTag(TAG_CONNECTION_PROGRESS_OVERLAY),
        ) {
            Column(
                modifier = Modifier
                    .width(CARD_WIDTH)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.connecting_overlay_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                ConnectionStage.entries.forEach { stage ->
                    StageRow(
                        stage = stage,
                        snapshot = snapshot,
                        textColor = MaterialTheme.colorScheme.onSurface,
                    )
                }

                snapshot.outcome?.failureText()?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                if (!snapshot.isFinished) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(TAG_CONNECTION_PROGRESS_CANCEL),
                    ) {
                        Text(text = stringResource(R.string.delete_neg))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(
    stage: ConnectionStage,
    snapshot: ConnectionProgress,
    textColor: Color,
) {
    val isCurrent = stage == snapshot.stage
    val isDone = stage.ordinal < snapshot.stage.ordinal ||
        snapshot.outcome is ConnectionOutcome.Succeeded
    val hasFailed = isCurrent && snapshot.isFinished &&
        snapshot.outcome !is ConnectionOutcome.Succeeded

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        // Column 1 — status symbol. Fixed width so the labels start on a common
        // left edge whatever symbol each row happens to be showing.
        Box(
            modifier = Modifier.width(SYMBOL_COLUMN_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasFailed -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )

                isDone -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )

                isCurrent && snapshot.waitingOnUser -> Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )

                isCurrent -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = textColor,
                    modifier = Modifier.size(16.dp),
                )

                else -> Spacer(modifier = Modifier.size(16.dp))
            }
        }

        // Column 2 — label, left justified, taking whatever width is left over.
        Text(
            text = stageLabel(stage, snapshot, isCurrent),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            // "Authenticating (keyboard-interactive)" is the realistic worst case and
            // needs two lines; anything longer is clipped rather than allowed to
            // stretch the card.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Stages not yet reached are dimmed rather than hidden, so the whole
            // shape of connecting is visible from the start.
            color = if (isDone || isCurrent) {
                textColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )

        // Column 3 — elapsed time. Always reserved, even before the counter appears
        // and on rows that will never have one, so the labels beside it do not shift
        // sideways the moment a stage starts running long.
        Box(
            modifier = Modifier.width(TIMER_COLUMN_WIDTH),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (isCurrent && !snapshot.isFinished) {
                ElapsedLabel(
                    stageStartedAtMillis = snapshot.stageStartedAtMillis,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun stageLabel(
    stage: ConnectionStage,
    snapshot: ConnectionProgress,
    isCurrent: Boolean,
): String {
    val base = stringResource(stage.labelRes())
    if (!isCurrent) return base
    if (snapshot.waitingOnUser) {
        return "$base — ${stringResource(R.string.connecting_waiting_for_you)}"
    }
    return snapshot.detail?.let { "$base ($it)" } ?: base
}

/**
 * A seconds counter for the running stage.
 *
 * This is the part that actually answers "is it stuck?", and is why the progress
 * snapshot carries a start timestamp rather than a precomputed duration: the clock
 * lives here and stops existing when the overlay does.
 */
@Composable
private fun ElapsedLabel(stageStartedAtMillis: Long, color: Color) {
    var now by remember(stageStartedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(stageStartedAtMillis) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val elapsed = now - stageStartedAtMillis
    if (elapsed < ELAPSED_VISIBLE_AFTER_MILLIS) return

    Text(
        text = stringResource(R.string.connecting_elapsed_seconds, (elapsed / 1000).toInt()),
        style = MaterialTheme.typography.bodySmall,
        color = color.copy(alpha = 0.7f),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun ConnectionOutcome.failureText(): String? = when (this) {
    is ConnectionOutcome.Succeeded -> null

    is ConnectionOutcome.Cancelled -> stringResource(R.string.connecting_cancelled)

    is ConnectionOutcome.TimedOut ->
        stringResource(R.string.connecting_timed_out, (budgetMillis / 1000).toInt())

    is ConnectionOutcome.Failed -> message ?: stringResource(R.string.connecting_failed)
}

@StringRes
private fun ConnectionStage.labelRes(): Int = when (this) {
    ConnectionStage.RESOLVING -> R.string.connecting_stage_resolving
    ConnectionStage.HANDSHAKING -> R.string.connecting_stage_handshaking
    ConnectionStage.VERIFYING_HOST_KEY -> R.string.connecting_stage_verifying_host_key
    ConnectionStage.AUTHENTICATING -> R.string.connecting_stage_authenticating
    ConnectionStage.OPENING_SESSION -> R.string.connecting_stage_opening_session
}
