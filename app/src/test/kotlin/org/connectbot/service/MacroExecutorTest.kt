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

package org.connectbot.service

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.terminal.VTermKey
import org.junit.Test

class MacroExecutorTest {

    /**
     * Records key, character, and text sends into one ordered log so tests can assert
     * interleaving across all three sinks, not just within one of them.
     */
    private class RecordingSink : MacroSink {
        val events = mutableListOf<String>()

        override fun sendKey(modifiers: Int, key: Int) {
            events += "key($modifiers,$key)"
        }

        override fun sendCharacter(modifiers: Int, codepoint: Int) {
            events += "char($modifiers,${codepoint.toChar()})"
        }

        override fun sendText(text: String) {
            events += "text($text)"
        }
    }

    private val sink = RecordingSink()
    private var finishedCount = 0
    private val executor = MacroExecutor(sink) { finishedCount++ }

    @Test
    fun ctrlPlusLetterDispatchesAsCharacter() {
        // The whole point of the feature: Ctrl+A has no VTermKey, so it must go through
        // the character path.
        executor.run(listOf(MacroStep.key(MacroKey.A, MacroModifiers.CTRL)))

        assertThat(sink.events).containsExactly("char(${MacroModifiers.CTRL},a)")
    }

    @Test
    fun shiftIsFoldedIntoTheCharacterAndDroppedFromTheMask() {
        executor.run(listOf(MacroStep.key(MacroKey.A, MacroModifiers.SHIFT)))

        assertThat(sink.events).containsExactly("char(0,A)")
    }

    @Test
    fun shiftUsesTheShiftedPunctuationRatherThanUppercasing() {
        // Character.toUpperCase('/') is '/', so a naive uppercase would send the wrong
        // byte here. The key table carries the real shifted code point.
        executor.run(listOf(MacroStep.key(MacroKey.SLASH, MacroModifiers.SHIFT)))

        assertThat(sink.events).containsExactly("char(0,?)")
    }

    @Test
    fun ctrlAndShiftTogetherKeepCtrlAndUppercase() {
        executor.run(
            listOf(MacroStep.key(MacroKey.C, MacroModifiers.CTRL or MacroModifiers.SHIFT)),
        )

        assertThat(sink.events).containsExactly("char(${MacroModifiers.CTRL},C)")
    }

    @Test
    fun specialKeysKeepTheirShiftBit() {
        // Shift+Tab is a genuinely distinct key for full-screen programs, so unlike a
        // printable character the bit must survive.
        executor.run(listOf(MacroStep.key(MacroKey.TAB, MacroModifiers.SHIFT)))

        assertThat(sink.events).containsExactly("key(${MacroModifiers.SHIFT},${VTermKey.TAB})")
    }

    @Test
    fun specialKeysCarryCombinedModifiers() {
        executor.run(
            listOf(MacroStep.key(MacroKey.F5, MacroModifiers.CTRL or MacroModifiers.ALT)),
        )

        val expected = MacroModifiers.CTRL or MacroModifiers.ALT
        assertThat(sink.events).containsExactly("key($expected,${VTermKey.FUNCTION_5})")
    }

    @Test
    fun textStepsAreSentVerbatim() {
        executor.run(listOf(MacroStep.text("ls -la")))

        assertThat(sink.events).containsExactly("text(ls -la)")
    }

    @Test
    fun mixedStepsPreserveOrderAcrossSinks() {
        // The tmux detach idiom: Ctrl+A, then "d".
        executor.run(
            listOf(
                MacroStep.key(MacroKey.A, MacroModifiers.CTRL),
                MacroStep.text("d"),
                MacroStep.key(MacroKey.ENTER),
            ),
        )

        assertThat(sink.events).containsExactly(
            "char(${MacroModifiers.CTRL},a)",
            "text(d)",
            "key(0,${VTermKey.ENTER})",
        )
    }

    @Test
    fun unknownKeyTokensAreSkippedRatherThanThrowing() {
        // Possible if the row was written by a newer version of the app.
        executor.run(
            listOf(
                MacroStep.key(MacroKey.A),
                MacroStep.key(MacroKey.B).copy(keyToken = "NOT_A_REAL_KEY"),
                MacroStep.key(MacroKey.C),
            ),
        )

        assertThat(sink.events).containsExactly("char(0,a)", "char(0,c)")
    }

    @Test
    fun blankTextStepsSendNothing() {
        executor.run(listOf(MacroStep.text("")))

        assertThat(sink.events).isEmpty()
    }

    @Test
    fun emptyMacroStillFinishes() {
        executor.run(emptyList())

        assertThat(sink.events).isEmpty()
        assertThat(finishedCount).isEqualTo(1)
    }

    @Test
    fun finishHookRunsOnceAfterAllSteps() {
        executor.run(listOf(MacroStep.key(MacroKey.A), MacroStep.key(MacroKey.B)))

        assertThat(sink.events).hasSize(2)
        assertThat(finishedCount).isEqualTo(1)
    }

    /**
     * Guards the fix for macro steps arriving out of order.
     *
     * termlib delivers key output asynchronously (`onKeyboardInput` does a `handler.post`)
     * while `TerminalBridge.injectString` reaches the transport channel synchronously. A
     * sink that mixed the two let a macro's text overtake the key press in front of it, so
     * "Ctrl+B then )" reached tmux as ") then Ctrl+B" and left a dangling prefix.
     *
     * `TerminalEmulatorMacroSink` is handed only the emulator, so it cannot reach
     * `injectString` at all — the ordering guarantee is structural rather than asserted
     * here. `TerminalEmulator` is a sealed interface and cannot be mocked, so what is
     * tested directly is the code-point splitting that path depends on.
     */
    @Test
    fun textIsSplitIntoCodePointsInOrder() {
        val seen = mutableListOf<Int>()

        forEachCodePoint("ls", seen::add)

        assertThat(seen).containsExactly('l'.code, 's'.code)
    }

    @Test
    fun charactersOutsideTheBasicPlaneStayOneCodePoint() {
        val rocket = "🚀"
        val seen = mutableListOf<Int>()

        forEachCodePoint(rocket, seen::add)

        // One code point, not two UTF-16 surrogate halves.
        assertThat(seen).containsExactly(rocket.codePointAt(0))
    }

    @Test
    fun splittingEmptyTextYieldsNothing() {
        val seen = mutableListOf<Int>()

        forEachCodePoint("", seen::add)

        assertThat(seen).isEmpty()
    }

    @Test
    fun macroDoesNotConsumeAUsersLockedModifier() {
        // A locked Ctrl belongs to the user's next real keystroke; running a macro must
        // leave it exactly as it was.
        val keyListener = TerminalKeyListener({ _, _ -> })
        keyListener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        keyListener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertThat(keyListener.getModifierState().ctrlState).isEqualTo(ModifierLevel.LOCKED)

        MacroExecutor(sink, onFinished = keyListener::clearTransients)
            .run(listOf(MacroStep.key(MacroKey.A, MacroModifiers.CTRL)))

        assertThat(keyListener.getModifierState().ctrlState).isEqualTo(ModifierLevel.LOCKED)
        // …and the macro used only its own modifiers, not the locked Ctrl on top.
        assertThat(sink.events).containsExactly("char(${MacroModifiers.CTRL},a)")
    }

    @Test
    fun macroClearsATransientModifierSoItCannotLeak() {
        val keyListener = TerminalKeyListener({ _, _ -> })
        keyListener.metaPress(TerminalKeyListener.SHIFT_ON, forceSticky = true)
        assertThat(keyListener.getModifierState().shiftState).isEqualTo(ModifierLevel.TRANSIENT)

        MacroExecutor(sink, onFinished = keyListener::clearTransients)
            .run(listOf(MacroStep.text("hi")))

        assertThat(keyListener.getModifierState().shiftState).isEqualTo(ModifierLevel.OFF)
    }
}
