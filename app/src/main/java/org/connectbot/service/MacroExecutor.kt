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

import org.connectbot.data.entity.MacroKeyDispatch
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind
import org.connectbot.terminal.TerminalEmulator

/**
 * Everything a macro can do to a session.
 *
 * A narrow interface rather than a [TerminalBridge] reference so [MacroExecutor] can be
 * unit tested against a recorder. Deliberately not folded into [KeyDispatcher]: macros
 * also send text, which is not a key dispatch at all.
 */
interface MacroSink {
    fun sendKey(modifiers: Int, key: Int)

    fun sendCharacter(modifiers: Int, codepoint: Int)

    fun sendText(text: String)
}

/**
 * Sends a macro's steps to a live terminal.
 *
 * Every step goes through the emulator, including literal text. That is load-bearing for
 * ordering: termlib delivers key output asynchronously — `onKeyboardInput` does a
 * `handler.post` before the bytes reach the bridge's transport channel — whereas
 * `TerminalBridge.injectString` reaches that channel synchronously. Mixing the two would
 * let the text of a macro overtake the key press in front of it, so "Ctrl+B then )" would
 * arrive as ") then Ctrl+B".
 *
 * Sending text as characters also makes a recorded macro replay exactly like typing it,
 * since termlib routes typed characters through `dispatchCharacter` too.
 *
 * Holding only the emulator — never the bridge — is what makes that ordering guarantee
 * structural: there is no `injectString` in reach to accidentally mix paths again.
 */
class TerminalEmulatorMacroSink(private val emulator: TerminalEmulator) : MacroSink {
    override fun sendKey(modifiers: Int, key: Int) = emulator.dispatchKey(modifiers, key)

    override fun sendCharacter(modifiers: Int, codepoint: Int) = emulator.dispatchCharacter(modifiers, codepoint)

    override fun sendText(text: String) = forEachCodePoint(text) { emulator.dispatchCharacter(0, it) }
}

/**
 * Walk [text] one Unicode code point at a time.
 *
 * Iterating chars would split a surrogate pair into two halves and send each as its own
 * bogus character.
 */
internal fun forEachCodePoint(text: String, action: (Int) -> Unit) {
    var i = 0
    while (i < text.length) {
        val codepoint = text.codePointAt(i)
        action(codepoint)
        i += Character.charCount(codepoint)
    }
}

/**
 * Plays back the steps of a user-defined macro.
 *
 * Execution is a plain synchronous loop with no inter-step delay, running on the caller's
 * thread the same way an ordinary key-row button press does.
 *
 * Ordering comes from every step taking the *same* route to the wire, not from spacing —
 * see [TerminalEmulatorMacroSink]. Sharing the bridge's serialized transport channel is
 * not on its own enough, because different routes reach that channel with different
 * latency.
 */
class MacroExecutor(
    private val sink: MacroSink,
    private val onFinished: () -> Unit = {},
) {
    /**
     * Play [steps] in order, then run the finish hook.
     *
     * Each step carries its own explicit modifiers and the executor never consults the
     * user's sticky Ctrl/Shift state: a macro is a fully specified combo, and silently
     * folding in an active modifier would make the same button send different things at
     * different times.
     */
    fun run(steps: List<MacroStep>) {
        steps.forEach(::runStep)
        onFinished()
    }

    private fun runStep(step: MacroStep) {
        when (step.kind) {
            MacroStepKind.TEXT -> step.text?.takeIf { it.isNotEmpty() }?.let(sink::sendText)

            // A null dispatch means an unrecognized key token, which happens if the row
            // was written by a newer version. Skip it rather than failing the macro.
            MacroStepKind.KEY -> when (val dispatch = step.key?.dispatch) {
                is MacroKeyDispatch.Special -> sink.sendKey(step.modifiers, dispatch.vtermKey)
                is MacroKeyDispatch.Character -> sendCharacter(step.modifiers, dispatch)
                null -> Unit
            }
        }
    }

    /**
     * Send a printable character.
     *
     * Shift is resolved into the character itself rather than passed along in the mask —
     * "a" becomes "A", "/" becomes "?" — which is how termlib handles a sticky Shift for
     * hardware keys, and how libvterm expects printable input. Leaving the bit set would
     * risk double-applying it.
     */
    private fun sendCharacter(modifiers: Int, dispatch: MacroKeyDispatch.Character) {
        val shifted = (modifiers and MacroModifiers.SHIFT) != 0
        val codepoint = if (shifted) dispatch.shiftedCodepoint else dispatch.codepoint
        sink.sendCharacter(modifiers and MacroModifiers.SHIFT.inv(), codepoint)
    }
}
