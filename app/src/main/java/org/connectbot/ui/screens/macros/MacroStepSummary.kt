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

import android.content.Context
import org.connectbot.R
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind

/** Separator between steps when summarizing a whole macro. */
private const val STEP_SEPARATOR = " · "

/**
 * Render a step as something like "Ctrl+A" or "\"ls -la\"".
 *
 * Takes a [Context] rather than being a composable so the same rendering can be reused in
 * accessibility descriptions and asserted directly in tests.
 */
fun macroStepSummary(context: Context, step: MacroStep): String = when (step.kind) {
    MacroStepKind.TEXT -> "\"${step.text.orEmpty()}\""

    MacroStepKind.KEY -> {
        val key = step.key
        if (key == null) {
            // Token written by a newer version of the app; the executor skips these too.
            "?"
        } else {
            (modifierNames(context, step.modifiers) + key.displayLabel).joinToString("+")
        }
    }
}

/** Render a whole step list, e.g. "Ctrl+A · \"d\"". */
fun macroSummary(context: Context, steps: List<MacroStep>): String = steps.joinToString(STEP_SEPARATOR) { macroStepSummary(context, it) }

private fun modifierNames(context: Context, modifiers: Int): List<String> = buildList {
    if (modifiers and MacroModifiers.CTRL != 0) add(context.getString(R.string.button_key_ctrl))
    if (modifiers and MacroModifiers.ALT != 0) add(context.getString(R.string.button_key_alt))
    if (modifiers and MacroModifiers.SHIFT != 0) add(context.getString(R.string.button_key_shift))
}
