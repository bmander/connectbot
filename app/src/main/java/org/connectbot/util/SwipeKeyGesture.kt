/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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
package org.connectbot.util

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val SWIPE_THRESHOLD = 100.dp

/**
 * Resolves a completed horizontal swipe into the key sequence to send,
 * or null if the swipe didn't meet the threshold or no keys are configured
 * for that direction.
 *
 * @param totalDragPx accumulated horizontal drag in pixels (negative = left, positive = right)
 * @param thresholdPx minimum absolute displacement to trigger a swipe
 * @param leftKeys parsed key sequence for swipe-left (empty = disabled)
 * @param rightKeys parsed key sequence for swipe-right (empty = disabled)
 * @return the key sequence to inject, or null if no action
 */
fun resolveSwipeKeys(
    totalDragPx: Float,
    thresholdPx: Float,
    leftKeys: String,
    rightKeys: String
): String? = when {
    totalDragPx < -thresholdPx && leftKeys.isNotEmpty() -> leftKeys
    totalDragPx > thresholdPx && rightKeys.isNotEmpty() -> rightKeys
    else -> null
}

/**
 * Modifier that detects horizontal swipe gestures and invokes [onSwipe]
 * with the configured key sequence for the swipe direction.
 *
 * Does nothing if both [leftKeys] and [rightKeys] are empty.
 */
fun Modifier.swipeKeyGesture(
    leftKeys: String,
    rightKeys: String,
    onSwipe: (String) -> Unit
): Modifier {
    if (leftKeys.isEmpty() && rightKeys.isEmpty()) return this
    return this.pointerInput(leftKeys, rightKeys) {
        val thresholdPx = SWIPE_THRESHOLD.toPx()
        var totalDragX = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDragX = 0f },
            onDragEnd = {
                resolveSwipeKeys(totalDragX, thresholdPx, leftKeys, rightKeys)
                    ?.let(onSwipe)
            },
            onDragCancel = { totalDragX = 0f },
            onHorizontalDrag = { _, dragAmount ->
                totalDragX += dragAmount
            }
        )
    }
}
