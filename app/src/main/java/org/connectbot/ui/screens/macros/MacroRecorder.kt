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

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.StateFlow
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind
import org.connectbot.service.KeyDispatcher
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalKeyListener

/**
 * Builds a macro's step list from real key presses.
 *
 * Rather than reimplementing a key picker, the editor shows the same on-screen key row the
 * terminal uses and lets the user type on the soft keyboard. This class stands in for the
 * terminal: it owns a [TerminalKeyListener] whose dispatcher records instead of writing to
 * a session, so Ctrl/Shift cycling, Esc, Tab and the function keys behave exactly as they
 * do in a live terminal — including the OFF → TRANSIENT → LOCKED toggle and the transient
 * clear after each key.
 */
class MacroRecorder {

    // Snapshot-backed so the recorder UI recomposes on each press without this class
    // having to publish change notifications of its own.
    private val recorded = mutableStateListOf<MacroStep>()

    private val keyListener = TerminalKeyListener(
        KeyDispatcher { modifiers, key -> recordSpecialKey(modifiers, key) },
    )

    /** Live modifier state, for tinting the Ctrl/Shift buttons in the key row. */
    val modifierState: StateFlow<ModifierState> get() = keyListener.modifierState

    val steps: List<MacroStep> get() = recorded

    /** Toggle a modifier, using the same three-state cycle as the terminal's key row. */
    fun pressModifier(code: Int) = keyListener.metaPress(code, forceSticky = true)

    /** Record a press of a key from the on-screen key row. */
    fun pressKey(vtermKey: Int) = keyListener.sendPressedKey(vtermKey)

    fun pressEscape() = keyListener.sendEscape()

    fun pressTab() = keyListener.sendTab()

    /**
     * Record a character typed on the soft keyboard.
     *
     * With no modifier held this extends the previous literal-text step, so typing
     * "ls -la" records as one text step rather than six key steps. With a modifier held it
     * becomes a key step instead, which is what makes Ctrl+A recordable by tapping Ctrl and
     * then "a".
     *
     * Characters outside the key table — emoji, accented letters — have no key step to map
     * to, so they are always recorded as text.
     */
    fun typeCharacter(codepoint: Int) {
        val modifiers = modifiersForCharacter()
        val resolved = MacroKey.fromCodepoint(codepoint)

        if (modifiers != MacroModifiers.NONE && resolved != null) {
            val (key, needsShift) = resolved
            val withShift =
                if (needsShift) modifiers or MacroModifiers.SHIFT else modifiers
            recorded += MacroStep.key(key, withShift)
        } else {
            appendText(String(Character.toChars(codepoint)))
        }

        keyListener.clearTransients()
    }

    /** Drop the most recent press, for fixing a mistake without starting over. */
    fun undo() {
        val last = recorded.lastOrNull() ?: return
        val text = last.text
        if (last.kind == MacroStepKind.TEXT && text != null && text.length > 1) {
            // Text steps accumulate characters, so undo peels off one character at a time
            // rather than discarding a whole typed run.
            recorded[recorded.lastIndex] = last.copy(text = text.dropLast(1))
        } else {
            recorded.removeAt(recorded.lastIndex)
        }
    }

    fun clear() = recorded.clear()

    private fun recordSpecialKey(modifiers: Int, vtermKey: Int) {
        val key = MacroKey.fromVTermKey(vtermKey) ?: return
        recorded += MacroStep.key(key, modifiers)
    }

    private fun modifiersForCharacter(): Int {
        var mask = MacroModifiers.NONE
        if (keyListener.isCtrlActive()) mask = mask or MacroModifiers.CTRL
        if (keyListener.isAltActive()) mask = mask or MacroModifiers.ALT
        // Shift is deliberately not read here: the soft keyboard already applies it to the
        // character itself, so "A" arrives as "A". Adding the bit as well would double it.
        return mask
    }

    private fun appendText(text: String) {
        val last = recorded.lastOrNull()
        if (last != null && last.kind == MacroStepKind.TEXT) {
            recorded[recorded.lastIndex] = last.copy(text = last.text.orEmpty() + text)
        } else {
            recorded += MacroStep.text(text)
        }
    }
}
