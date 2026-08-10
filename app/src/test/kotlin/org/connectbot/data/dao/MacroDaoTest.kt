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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MacroDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var macroDao: MacroDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        macroDao = database.macroDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndRetrieveMacroWithSteps() = runTest {
        val id = macroDao.save(
            Macro(label = "detach"),
            listOf(
                MacroStep.key(MacroKey.A, MacroModifiers.CTRL),
                MacroStep.text("d"),
            ),
        )

        assertThat(id).isGreaterThan(0)

        val retrieved = macroDao.getById(id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.macro?.label).isEqualTo("detach")
        assertThat(retrieved?.steps).hasSize(2)

        val first = retrieved!!.steps[0]
        assertThat(first.kind).isEqualTo(MacroStepKind.KEY)
        assertThat(first.key).isEqualTo(MacroKey.A)
        assertThat(first.modifiers).isEqualTo(MacroModifiers.CTRL)

        val second = retrieved.steps[1]
        assertThat(second.kind).isEqualTo(MacroStepKind.TEXT)
        assertThat(second.text).isEqualTo("d")
    }

    @Test
    fun saveRenumbersStepPositionsDensely() = runTest {
        val id = macroDao.save(
            Macro(label = "m"),
            listOf(
                MacroStep.key(MacroKey.F1).copy(position = 99),
                MacroStep.key(MacroKey.F2).copy(position = 4),
                MacroStep.key(MacroKey.F3).copy(position = 7),
            ),
        )

        val steps = macroDao.getById(id)!!.steps
        assertThat(steps.map { it.position }).containsExactly(0, 1, 2)
        assertThat(steps.map { it.key }).containsExactly(MacroKey.F1, MacroKey.F2, MacroKey.F3)
    }

    /**
     * Room does not honor ORDER BY inside a @Relation, so `MacroWithSteps.steps` sorts
     * defensively. Insert steps whose row order differs from their position order to
     * prove the sort is actually doing the work.
     */
    @Test
    fun stepsAreReturnedInPositionOrderRegardlessOfRowOrder() = runTest {
        val macroId = macroDao.insertOrUpdateMacro(Macro(label = "m"))
        macroDao.insertSteps(
            listOf(
                MacroStep.key(MacroKey.C).copy(macroId = macroId, position = 2),
                MacroStep.key(MacroKey.A).copy(macroId = macroId, position = 0),
                MacroStep.key(MacroKey.B).copy(macroId = macroId, position = 1),
            ),
        )

        val steps = macroDao.getById(macroId)!!.steps
        assertThat(steps.map { it.key }).containsExactly(MacroKey.A, MacroKey.B, MacroKey.C)
    }

    @Test
    fun saveReplacesStepsRatherThanAppending() = runTest {
        val id = macroDao.save(Macro(label = "m"), listOf(MacroStep.key(MacroKey.A), MacroStep.key(MacroKey.B)))

        macroDao.save(Macro(id = id, label = "m"), listOf(MacroStep.key(MacroKey.Z)))

        val steps = macroDao.getById(id)!!.steps
        assertThat(steps).hasSize(1)
        assertThat(steps[0].key).isEqualTo(MacroKey.Z)
    }

    @Test
    fun deletingMacroCascadesToSteps() = runTest {
        val id = macroDao.save(Macro(label = "m"), listOf(MacroStep.key(MacroKey.A), MacroStep.key(MacroKey.B)))

        val deleted = macroDao.deleteById(id)

        assertThat(deleted).isEqualTo(1)
        assertThat(macroDao.getById(id)).isNull()
        val orphanCount = database.query("SELECT COUNT(*) FROM macro_steps", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertThat(orphanCount).isEqualTo(0)
    }

    @Test
    fun newMacrosObserveInPositionOrder() = runTest {
        macroDao.save(Macro(label = "second", position = 1), emptyList())
        macroDao.save(Macro(label = "first", position = 0), emptyList())

        val macros = macroDao.observeAll().first()

        assertThat(macros.map { it.macro.label }).containsExactly("first", "second")
    }

    @Test
    fun reorderPersistsNewPositions() = runTest {
        val a = macroDao.save(Macro(label = "a", position = 0), emptyList())
        val b = macroDao.save(Macro(label = "b", position = 1), emptyList())
        val c = macroDao.save(Macro(label = "c", position = 2), emptyList())

        macroDao.reorder(listOf(c, a, b))

        val macros = macroDao.observeAll().first()
        assertThat(macros.map { it.macro.label }).containsExactly("c", "a", "b")
    }

    @Test
    fun nextPositionAppendsAfterExistingMacros() = runTest {
        assertThat(macroDao.nextPosition()).isEqualTo(0)

        macroDao.save(Macro(label = "a", position = 0), emptyList())
        macroDao.save(Macro(label = "b", position = 5), emptyList())

        assertThat(macroDao.nextPosition()).isEqualTo(6)
    }
}
