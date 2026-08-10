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

package org.connectbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps

/**
 * Data Access Object for macro buttons and their steps.
 */
@Dao
interface MacroDao {
    /**
     * Observe all macros with their steps, in row order.
     */
    @Transaction
    @Query("SELECT * FROM macros ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<MacroWithSteps>>

    /**
     * Get all macros with their steps (one-time query).
     */
    @Transaction
    @Query("SELECT * FROM macros ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<MacroWithSteps>

    /**
     * Get a single macro with its steps.
     */
    @Transaction
    @Query("SELECT * FROM macros WHERE id = :macroId")
    suspend fun getById(macroId: Long): MacroWithSteps?

    /**
     * The position a newly created macro should take, placing it last in the row.
     */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM macros")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMacro(macro: Macro): Long

    @Insert
    suspend fun insertSteps(steps: List<MacroStep>)

    @Query("DELETE FROM macro_steps WHERE macro_id = :macroId")
    suspend fun deleteStepsFor(macroId: Long)

    @Query("DELETE FROM macros WHERE id = :macroId")
    suspend fun deleteById(macroId: Long): Int

    @Query("UPDATE macros SET position = :position WHERE id = :macroId")
    suspend fun updatePosition(macroId: Long, position: Int)

    /**
     * Insert or update a macro and replace its entire step list.
     *
     * Steps are renumbered densely from 0 in the order given, so callers only need to
     * supply the list in the order they want it played back.
     *
     * @return the macro's ID, which is newly assigned when [macro] had ID 0
     */
    @Transaction
    suspend fun save(macro: Macro, steps: List<MacroStep>): Long {
        val macroId = insertOrUpdateMacro(macro)
        deleteStepsFor(macroId)
        insertSteps(
            steps.mapIndexed { index, step ->
                step.copy(id = 0, macroId = macroId, position = index)
            },
        )
        return macroId
    }

    /**
     * Persist a whole reordering of the macro row in one transaction.
     */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}
