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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined macro button shown in the row above the terminal key row.
 *
 * The macro itself carries only presentation and ordering; what it actually sends
 * lives in its [MacroStep] rows, joined via [MacroWithSteps].
 *
 * @property id Database ID of the macro
 * @property label Text drawn on the button, e.g. "^C" or "detach"
 * @property position Left-to-right ordering within the macro row
 */
@Entity(tableName = "macros")
data class Macro(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val label: String,

    val position: Int = 0,
)
