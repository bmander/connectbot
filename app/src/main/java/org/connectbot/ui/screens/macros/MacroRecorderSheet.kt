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

package org.connectbot.ui.screens.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.MacroStep
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey
import org.connectbot.ui.components.TerminalKeyboardContent

object MacroRecorderTestTags {
    const val SHEET = "macro_recorder_sheet"
    const val CAPTURE = "macro_recorder_capture"
    const val PREVIEW = "macro_recorder_preview"
    const val UNDO = "macro_recorder_undo"
    const val DONE = "macro_recorder_done"
}

/**
 * Records macro steps from real key presses.
 *
 * The body is the same on-screen key row the terminal shows, plus an invisible capture
 * field that keeps the soft keyboard up. Pressing keys here builds the step list exactly as
 * pressing them in a session would send them.
 */
@Composable
fun MacroRecorderDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<MacroStep>) -> Unit,
) {
    val recorder = remember { MacroRecorder() }
    val steps = recorder.steps
    val modifierState by recorder.modifierState.collectAsState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var imeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MacroRecorderTestTags.SHEET),
        title = { Text(stringResource(R.string.macro_recorder_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.macro_recorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (steps.isEmpty()) {
                            stringResource(R.string.macro_recorder_empty)
                        } else {
                            macroSummary(context, steps)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (steps.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.testTag(MacroRecorderTestTags.PREVIEW),
                    )
                }

                CaptureField(
                    focusRequester = focusRequester,
                    onCharacter = { codepoint -> recorder.typeCharacter(codepoint) },
                    onBackspace = { recorder.pressKey(VTermKey.BACKSPACE) },
                    onEnter = { recorder.pressKey(VTermKey.ENTER) },
                )

                TerminalKeyboardContent(
                    modifierState = modifierState,
                    onCtrlPress = { recorder.pressModifier(TerminalKeyListener.CTRL_ON) },
                    onShiftPress = {
                        recorder.pressModifier(TerminalKeyListener.SHIFT_ON)
                    },
                    onEscPress = { recorder.pressEscape() },
                    onTabPress = { recorder.pressTab() },
                    onKeyPress = { key -> recorder.pressKey(key) },
                    onInteraction = {},
                    onHideIme = { imeVisible = false },
                    onShowIme = {
                        imeVisible = true
                        focusRequester.requestFocus()
                    },
                    onOpenTextInput = {},
                    onScrollInProgressChange = {},
                    imeVisible = imeVisible,
                    playAnimation = false,
                    bumpyArrows = false,
                    showTextInputButton = false,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { recorder.undo() },
                        enabled = steps.isNotEmpty(),
                        modifier = Modifier.testTag(MacroRecorderTestTags.UNDO),
                    ) {
                        Text(stringResource(R.string.macro_recorder_undo))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = steps.isNotEmpty(),
                onClick = { onConfirm(steps.toList()) },
                modifier = Modifier.testTag(MacroRecorderTestTags.DONE),
            ) {
                Text(stringResource(R.string.macro_recorder_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        },
    )
}

/**
 * A one-pixel field that exists only to hold IME focus and hand back what was typed.
 *
 * Its value is reset to empty after every change, so the IME never accumulates a word to
 * autocorrect or predict against — important because Gboard will otherwise happily rewrite
 * a typed "d" into "did " and that would be recorded verbatim. [KeyboardType.Password] is
 * belt-and-braces: it suppresses the suggestion strip on IMEs that ignore
 * `autoCorrectEnabled`.
 */
@Composable
private fun CaptureField(
    focusRequester: FocusRequester,
    onCharacter: (Int) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }

    BasicTextField(
        value = value,
        onValueChange = { new ->
            val text = new.text
            when {
                text.isEmpty() -> Unit

                // A newline can only come from the IME's Enter key.
                text == "\n" -> onEnter()

                else -> text.codePoints().forEach { codepoint ->
                    if (codepoint == '\n'.code) onEnter() else onCharacter(codepoint)
                }
            }
            value = TextFieldValue("")
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        textStyle = TextStyle.Default,
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .testTag(MacroRecorderTestTags.CAPTURE)
            .onPreviewKeyEvent { event ->
                // The field is always empty, so a backspace produces no text change and
                // arrives as a bare key event instead.
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                    onBackspace()
                    true
                } else {
                    false
                }
            },
    )
}
