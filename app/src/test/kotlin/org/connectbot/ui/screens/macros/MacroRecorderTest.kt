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

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStepKind
import org.connectbot.service.ModifierLevel
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey
import org.junit.Test

class MacroRecorderTest {

    private val recorder = MacroRecorder()

    private fun type(text: String) = text.forEach { recorder.typeCharacter(it.code) }

    @Test
    fun typingPlainTextRecordsOneTextStep() {
        type("ls -la")

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].kind).isEqualTo(MacroStepKind.TEXT)
        assertThat(recorder.steps[0].text).isEqualTo("ls -la")
    }

    @Test
    fun ctrlThenLetterRecordsAKeyStep() {
        // The tmux prefix: tap Ctrl on the key row, then "a" on the soft keyboard.
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("a")

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].kind).isEqualTo(MacroStepKind.KEY)
        assertThat(recorder.steps[0].key).isEqualTo(MacroKey.A)
        assertThat(recorder.steps[0].modifiers).isEqualTo(MacroModifiers.CTRL)
    }

    @Test
    fun theTmuxDetachSequenceRecordsAsCtrlAThenText() {
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("a")
        type("d")

        assertThat(recorder.steps).hasSize(2)
        assertThat(recorder.steps[0].key).isEqualTo(MacroKey.A)
        assertThat(recorder.steps[0].modifiers).isEqualTo(MacroModifiers.CTRL)
        assertThat(recorder.steps[1].text).isEqualTo("d")
    }

    @Test
    fun aTransientModifierAppliesToOnlyTheNextCharacter() {
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("ab")

        assertThat(recorder.steps).hasSize(2)
        assertThat(recorder.steps[0].modifiers).isEqualTo(MacroModifiers.CTRL)
        assertThat(recorder.steps[1].text).isEqualTo("b")
    }

    @Test
    fun aLockedModifierAppliesToEveryFollowingCharacter() {
        // Two taps on Ctrl locks it, matching the key row's OFF -> TRANSIENT -> LOCKED cycle.
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        assertThat(recorder.modifierState.value.ctrlState).isEqualTo(ModifierLevel.LOCKED)

        type("ab")

        assertThat(recorder.steps).hasSize(2)
        assertThat(recorder.steps.map { it.key }).containsExactly(MacroKey.A, MacroKey.B)
        assertThat(recorder.steps.map { it.modifiers })
            .containsExactly(MacroModifiers.CTRL, MacroModifiers.CTRL)
    }

    @Test
    fun capitalLettersFromTheSoftKeyboardBecomeShiftedKeySteps() {
        // The IME already applied Shift, so "A" arrives as a capital. Under Ctrl it must
        // still round-trip to key A with Shift set, not to a doubled Shift.
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("A")

        assertThat(recorder.steps[0].key).isEqualTo(MacroKey.A)
        assertThat(recorder.steps[0].modifiers)
            .isEqualTo(MacroModifiers.CTRL or MacroModifiers.SHIFT)
    }

    @Test
    fun shiftedPunctuationRoundTripsToItsUnshiftedKey() {
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("?")

        assertThat(recorder.steps[0].key).isEqualTo(MacroKey.SLASH)
        assertThat(recorder.steps[0].modifiers)
            .isEqualTo(MacroModifiers.CTRL or MacroModifiers.SHIFT)
    }

    @Test
    fun unmodifiedCapitalsStayLiteralText() {
        type("Hello")

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].text).isEqualTo("Hello")
    }

    @Test
    fun charactersOutsideTheKeyTableAreRecordedAsText() {
        // Emoji and accented letters have no key step to map to.
        recorder.pressModifier(TerminalKeyListener.CTRL_ON)
        type("é")

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].kind).isEqualTo(MacroStepKind.TEXT)
        assertThat(recorder.steps[0].text).isEqualTo("é")
    }

    @Test
    fun keyRowPressesRecordAsSpecialKeys() {
        recorder.pressEscape()
        recorder.pressTab()
        recorder.pressKey(VTermKey.UP)
        recorder.pressKey(VTermKey.FUNCTION_5)

        assertThat(recorder.steps.map { it.key })
            .containsExactly(MacroKey.ESCAPE, MacroKey.TAB, MacroKey.UP, MacroKey.F5)
        assertThat(recorder.steps.all { it.modifiers == MacroModifiers.NONE }).isTrue()
    }

    @Test
    fun shiftTabRecordsWithItsModifier() {
        recorder.pressModifier(TerminalKeyListener.SHIFT_ON)
        recorder.pressTab()

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].key).isEqualTo(MacroKey.TAB)
        assertThat(recorder.steps[0].modifiers).isEqualTo(MacroModifiers.SHIFT)
    }

    @Test
    fun aKeyRowPressBreaksTheTextRun() {
        type("ls")
        recorder.pressKey(VTermKey.ENTER)
        type("pwd")

        assertThat(recorder.steps.map { it.kind }).containsExactly(
            MacroStepKind.TEXT,
            MacroStepKind.KEY,
            MacroStepKind.TEXT,
        )
        assertThat(recorder.steps[0].text).isEqualTo("ls")
        assertThat(recorder.steps[2].text).isEqualTo("pwd")
    }

    @Test
    fun undoPeelsOffOneCharacterAtATimeWithinATextRun() {
        type("lss")

        recorder.undo()

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].text).isEqualTo("ls")
    }

    @Test
    fun undoRemovesAWholeKeyStep() {
        type("ls")
        recorder.pressKey(VTermKey.ENTER)

        recorder.undo()

        assertThat(recorder.steps).hasSize(1)
        assertThat(recorder.steps[0].text).isEqualTo("ls")
    }

    @Test
    fun undoOnASingleCharacterTextStepRemovesTheStep() {
        type("l")

        recorder.undo()

        assertThat(recorder.steps).isEmpty()
    }

    @Test
    fun undoOnAnEmptyRecordingIsANoOp() {
        recorder.undo()

        assertThat(recorder.steps).isEmpty()
    }
}
