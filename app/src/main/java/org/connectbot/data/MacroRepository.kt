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

package org.connectbot.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.connectbot.data.dao.MacroDao
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps
import org.connectbot.di.CoroutineDispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user-defined macro buttons.
 *
 * @param macroDao The DAO for accessing macro data
 */
@Singleton
class MacroRepository @Inject constructor(
    private val macroDao: MacroDao,
    private val dispatchers: CoroutineDispatchers,
) {
    /**
     * Observe all macros with their steps, in row order.
     */
    fun observeAll(): Flow<List<MacroWithSteps>> = macroDao.observeAll()

    /**
     * Get all macros with their steps.
     */
    suspend fun getAll(): List<MacroWithSteps> = withContext(dispatchers.io) {
        macroDao.getAll()
    }

    /**
     * Get a macro with its steps by ID.
     */
    suspend fun getById(macroId: Long): MacroWithSteps? = withContext(dispatchers.io) {
        macroDao.getById(macroId)
    }

    /**
     * Insert or update a macro and replace its steps.
     *
     * New macros ([Macro.id] of 0) are appended to the end of the row.
     *
     * @return the macro's ID
     */
    suspend fun save(macro: Macro, steps: List<MacroStep>): Long = withContext(dispatchers.io) {
        val toSave = if (macro.id == 0L) macro.copy(position = macroDao.nextPosition()) else macro
        macroDao.save(toSave, steps)
    }

    /**
     * Delete a macro and, by cascade, its steps.
     */
    suspend fun delete(macroId: Long) = withContext(dispatchers.io) {
        macroDao.deleteById(macroId)
        Unit
    }

    /**
     * Persist a new left-to-right ordering of the macro row.
     */
    suspend fun reorder(orderedIds: List<Long>) = withContext(dispatchers.io) {
        macroDao.reorder(orderedIds)
    }
}
