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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps

private const val UI_OPACITY = 0.5f

/**
 * Height of the macro key row in dp. Matches [TERMINAL_KEYBOARD_HEIGHT_DP] so the two
 * rows stack evenly.
 */
const val MACRO_ROW_HEIGHT_DP = 30

/** Minimum width of a macro button; labels are free-form so buttons grow to fit. */
private const val MACRO_BUTTON_MIN_WIDTH_DP = 45

/** Longest label a button will show before ellipsizing. */
private const val MACRO_BUTTON_MAX_WIDTH_DP = 140

const val MACRO_KEY_ROW_TEST_TAG = "macro_key_row"

/**
 * Row of user-defined macro buttons, sitting directly above the standard terminal key row.
 *
 * Unlike the standard row this has no fixed key width: macro labels are user-supplied and
 * vary in length, so buttons size to their text between a min and max width.
 *
 * Callers are expected to skip this entirely when [macros] is empty rather than rendering
 * an empty strip — see `ConsoleTerminalPage`, which also drops the matching terminal
 * padding in that case.
 */
@Composable
fun MacroKeyRow(
    macros: List<MacroWithSteps>,
    onMacroClick: (MacroWithSteps) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .testTag(MACRO_KEY_ROW_TEST_TAG)
            .pointerInput(Unit) {
                // Reset the auto-hide timer on any touch, matching TerminalKeyboard.
                detectTapGestures(
                    onPress = {
                        onInteraction()
                        tryAwaitRelease()
                    },
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MACRO_ROW_HEIGHT_DP.dp)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            macros.forEach { macro ->
                MacroButton(
                    macro = macro,
                    onClick = {
                        onMacroClick(macro)
                        onInteraction()
                    },
                )
            }
        }
    }
}

@Composable
private fun MacroButton(
    macro: MacroWithSteps,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.macro_button_description, macro.macro.label)

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(MACRO_ROW_HEIGHT_DP.dp)
            .widthIn(min = MACRO_BUTTON_MIN_WIDTH_DP.dp, max = MACRO_BUTTON_MAX_WIDTH_DP.dp)
            .testTag("macro_${macro.macro.id}")
            .semantics { contentDescription = description },
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = macro.macro.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun previewMacro(id: Long, label: String) = MacroWithSteps(
    macro = Macro(id = id, label = label, position = id.toInt()),
    stepsUnordered = listOf(MacroStep.key(MacroKey.C, MacroModifiers.CTRL)),
)

@Preview(name = "Macro Key Row", showBackground = true)
@Composable
private fun MacroKeyRowPreview() {
    MaterialTheme {
        MacroKeyRow(
            macros = listOf(
                previewMacro(1, "^C"),
                previewMacro(2, "detach"),
                previewMacro(3, "ll"),
            ),
            onMacroClick = {},
            onInteraction = {},
        )
    }
}

@Preview(name = "Macro Key Row - Long Labels", showBackground = true)
@Composable
private fun MacroKeyRowLongLabelsPreview() {
    MaterialTheme {
        MacroKeyRow(
            macros = listOf(
                previewMacro(1, "a"),
                previewMacro(2, "a very long macro label that must ellipsize"),
            ),
            onMacroClick = {},
            onInteraction = {},
        )
    }
}
