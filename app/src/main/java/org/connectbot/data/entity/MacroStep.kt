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

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** What a single macro step does. */
enum class MacroStepKind {
    /** Send one key, optionally with modifiers. */
    KEY,

    /** Type a literal string. */
    TEXT,
}

/**
 * Modifier bits for a [MacroStep].
 *
 * Values deliberately match the VTerm modifier mask so no translation is needed on the
 * way to the terminal. Mirrors the private constants in
 * `org.connectbot.service.TerminalKeyListener`.
 */
object MacroModifiers {
    const val NONE = 0
    const val SHIFT = 1
    const val ALT = 2
    const val CTRL = 4
}

/**
 * One step of a [Macro].
 *
 * A step is either a key press ([MacroStepKind.KEY], using [keyToken] and [modifiers]) or a
 * literal string ([MacroStepKind.TEXT], using [text]). The unused fields are null.
 *
 * @property macroId Owning macro; steps are deleted with their macro
 * @property position 0-based order within the macro
 * @property keyToken A [MacroKey] name, for KEY steps
 * @property modifiers Bitmask from [MacroModifiers]; always 0 for TEXT steps
 * @property text Literal text, for TEXT steps
 */
@Entity(
    tableName = "macro_steps",
    foreignKeys = [
        ForeignKey(
            entity = Macro::class,
            parentColumns = ["id"],
            childColumns = ["macro_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macro_id")],
)
data class MacroStep(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "macro_id")
    val macroId: Long = 0,

    val position: Int = 0,

    val kind: MacroStepKind,

    @ColumnInfo(name = "key_token")
    val keyToken: String? = null,

    @ColumnInfo(defaultValue = "0")
    val modifiers: Int = MacroModifiers.NONE,

    val text: String? = null,
) {
    /**
     * The key this step sends, or null for TEXT steps and for tokens this build does not
     * recognize (possible if the row was written by a newer version).
     */
    val key: MacroKey? get() = MacroKey.fromToken(keyToken)

    companion object {
        fun key(key: MacroKey, modifiers: Int = MacroModifiers.NONE) = MacroStep(kind = MacroStepKind.KEY, keyToken = key.name, modifiers = modifiers)

        fun text(text: String) = MacroStep(kind = MacroStepKind.TEXT, text = text)
    }
}

/**
 * A macro together with its steps.
 *
 * Room does not honor `ORDER BY` inside a `@Relation`, so [stepsUnordered] arrives in
 * arbitrary order and callers must read [steps] instead.
 */
data class MacroWithSteps(
    @Embedded val macro: Macro,
    @Relation(parentColumn = "id", entityColumn = "macro_id")
    val stepsUnordered: List<MacroStep> = emptyList(),
) {
    // Sorted once per row rather than on every read: the macro list re-reads this on each
    // recomposition to render its step summary.
    val steps: List<MacroStep> by lazy(LazyThreadSafetyMode.NONE) {
        stepsUnordered.sortedBy { it.position }
    }
}
