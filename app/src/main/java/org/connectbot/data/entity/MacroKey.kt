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

package org.connectbot.data.entity

import org.connectbot.terminal.VTermKey

/**
 * How a [MacroKey] reaches the terminal.
 *
 * libvterm has two separate entry points and the choice is not interchangeable:
 * named keys go through `dispatchKey` with a [VTermKey] constant, while printable
 * characters go through `dispatchCharacter` with a Unicode code point. There is no
 * [VTermKey] for the letter "a", which is why Ctrl+A cannot be expressed as a
 * `dispatchKey` call at all.
 */
sealed interface MacroKeyDispatch {
    /** A named key such as Esc or F5, sent as a [VTermKey] constant. */
    data class Special(val vtermKey: Int) : MacroKeyDispatch

    /**
     * A printable character.
     *
     * [shiftedCodepoint] is what the key produces with Shift held, mirroring a real
     * US keyboard: "a" becomes "A", "/" becomes "?". termlib resolves Shift the same
     * way for hardware keys — it folds Shift into the code point via the
     * KeyCharacterMap rather than leaving it to the terminal.
     */
    data class Character(val codepoint: Int, val shiftedCodepoint: Int = codepoint) : MacroKeyDispatch
}

/**
 * The set of keys a macro step can send.
 *
 * The enum *name* is the stable on-disk token stored in [MacroStep.keyToken]. Deliberately
 * not the raw [VTermKey] integer: those constants come from the external termlib artifact,
 * and persisting them would tie the database schema to that library's numbering.
 *
 * @property displayLabel Human-readable name, used when summarizing a macro
 * @property dispatch How to deliver the key to the terminal
 */
enum class MacroKey(
    val displayLabel: String,
    val dispatch: MacroKeyDispatch,
) {
    ESCAPE("Esc", MacroKeyDispatch.Special(VTermKey.ESCAPE)),
    TAB("Tab", MacroKeyDispatch.Special(VTermKey.TAB)),
    ENTER("Enter", MacroKeyDispatch.Special(VTermKey.ENTER)),
    BACKSPACE("Bksp", MacroKeyDispatch.Special(VTermKey.BACKSPACE)),
    DELETE("Del", MacroKeyDispatch.Special(VTermKey.DEL)),
    INSERT("Ins", MacroKeyDispatch.Special(VTermKey.INS)),
    UP("↑", MacroKeyDispatch.Special(VTermKey.UP)),
    DOWN("↓", MacroKeyDispatch.Special(VTermKey.DOWN)),
    LEFT("←", MacroKeyDispatch.Special(VTermKey.LEFT)),
    RIGHT("→", MacroKeyDispatch.Special(VTermKey.RIGHT)),
    HOME("Home", MacroKeyDispatch.Special(VTermKey.HOME)),
    END("End", MacroKeyDispatch.Special(VTermKey.END)),
    PAGE_UP("PgUp", MacroKeyDispatch.Special(VTermKey.PAGEUP)),
    PAGE_DOWN("PgDn", MacroKeyDispatch.Special(VTermKey.PAGEDOWN)),
    F1("F1", MacroKeyDispatch.Special(VTermKey.FUNCTION_1)),
    F2("F2", MacroKeyDispatch.Special(VTermKey.FUNCTION_2)),
    F3("F3", MacroKeyDispatch.Special(VTermKey.FUNCTION_3)),
    F4("F4", MacroKeyDispatch.Special(VTermKey.FUNCTION_4)),
    F5("F5", MacroKeyDispatch.Special(VTermKey.FUNCTION_5)),
    F6("F6", MacroKeyDispatch.Special(VTermKey.FUNCTION_6)),
    F7("F7", MacroKeyDispatch.Special(VTermKey.FUNCTION_7)),
    F8("F8", MacroKeyDispatch.Special(VTermKey.FUNCTION_8)),
    F9("F9", MacroKeyDispatch.Special(VTermKey.FUNCTION_9)),
    F10("F10", MacroKeyDispatch.Special(VTermKey.FUNCTION_10)),
    F11("F11", MacroKeyDispatch.Special(VTermKey.FUNCTION_11)),
    F12("F12", MacroKeyDispatch.Special(VTermKey.FUNCTION_12)),

    A("A", letter('a')),
    B("B", letter('b')),
    C("C", letter('c')),
    D("D", letter('d')),
    E("E", letter('e')),
    F("F", letter('f')),
    G("G", letter('g')),
    H("H", letter('h')),
    I("I", letter('i')),
    J("J", letter('j')),
    K("K", letter('k')),
    L("L", letter('l')),
    M("M", letter('m')),
    N("N", letter('n')),
    O("O", letter('o')),
    P("P", letter('p')),
    Q("Q", letter('q')),
    R("R", letter('r')),
    S("S", letter('s')),
    T("T", letter('t')),
    U("U", letter('u')),
    V("V", letter('v')),
    W("W", letter('w')),
    X("X", letter('x')),
    Y("Y", letter('y')),
    Z("Z", letter('z')),

    DIGIT_1("1", pair('1', '!')),
    DIGIT_2("2", pair('2', '@')),
    DIGIT_3("3", pair('3', '#')),
    DIGIT_4("4", pair('4', '$')),
    DIGIT_5("5", pair('5', '%')),
    DIGIT_6("6", pair('6', '^')),
    DIGIT_7("7", pair('7', '&')),
    DIGIT_8("8", pair('8', '*')),
    DIGIT_9("9", pair('9', '(')),
    DIGIT_0("0", pair('0', ')')),

    SPACE("Space", pair(' ', ' ')),
    MINUS("-", pair('-', '_')),
    EQUALS("=", pair('=', '+')),
    LEFT_BRACKET("[", pair('[', '{')),
    RIGHT_BRACKET("]", pair(']', '}')),
    BACKSLASH("\\", pair('\\', '|')),
    SEMICOLON(";", pair(';', ':')),
    APOSTROPHE("'", pair('\'', '"')),
    GRAVE("`", pair('`', '~')),
    COMMA(",", pair(',', '<')),
    PERIOD(".", pair('.', '>')),
    SLASH("/", pair('/', '?')),
    ;

    companion object {
        private val byToken: Map<String, MacroKey> = entries.associateBy { it.name }

        private val bySpecialKey: Map<Int, MacroKey> = entries
            .mapNotNull { key ->
                (key.dispatch as? MacroKeyDispatch.Special)?.let { it.vtermKey to key }
            }
            .toMap()

        private val byCodepoint: Map<Int, MacroKey> = entries
            .mapNotNull { key ->
                (key.dispatch as? MacroKeyDispatch.Character)?.let { it.codepoint to key }
            }
            .toMap()

        private val byShiftedCodepoint: Map<Int, MacroKey> = entries
            .mapNotNull { key ->
                (key.dispatch as? MacroKeyDispatch.Character)
                    ?.takeIf { it.shiftedCodepoint != it.codepoint }
                    ?.let { it.shiftedCodepoint to key }
            }
            .toMap()

        /**
         * Resolve a stored token back to a key.
         *
         * Returns null for tokens this build does not know about, which can happen if the
         * database was written by a newer version. Callers skip such steps rather than fail.
         */
        fun fromToken(token: String?): MacroKey? = token?.let { byToken[it] }

        /** Resolve a [VTermKey] constant seen during recording back to a key. */
        fun fromVTermKey(vtermKey: Int): MacroKey? = bySpecialKey[vtermKey]

        /**
         * Resolve a typed character back to a key, reporting whether Shift is needed to
         * produce it.
         *
         * A capital "A" and a "?" both come off the soft keyboard as plain code points, so
         * recording them faithfully means recovering the unshifted key plus the Shift bit.
         * Returns null for characters outside the key table, such as emoji or accented
         * letters, which callers record as literal text instead.
         */
        fun fromCodepoint(codepoint: Int): Pair<MacroKey, Boolean>? {
            byCodepoint[codepoint]?.let { return it to false }
            byShiftedCodepoint[codepoint]?.let { return it to true }
            return null
        }
    }
}

private fun letter(lower: Char) = MacroKeyDispatch.Character(lower.code, lower.uppercaseChar().code)

private fun pair(unshifted: Char, shifted: Char) = MacroKeyDispatch.Character(unshifted.code, shifted.code)
