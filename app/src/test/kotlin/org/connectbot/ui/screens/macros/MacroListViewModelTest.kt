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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.MacroRepository
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MacroListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var macroRepository: MacroRepository

    private val macros = listOf(
        macro(1, "a"),
        macro(2, "b"),
        macro(3, "c"),
    )

    private fun macro(id: Long, label: String) = MacroWithSteps(
        macro = Macro(id = id, label = label, position = (id - 1).toInt()),
        stepsUnordered = listOf(MacroStep.key(MacroKey.A)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        macroRepository = mock()
        whenever(macroRepository.observeAll()).thenReturn(flowOf(macros))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun macrosAreLoadedFromTheRepository() {
        val state = MacroListViewModel(macroRepository).uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.macros.map { it.macro.label }).containsExactly("a", "b", "c")
    }

    @Test
    fun moveUpSwapsWithThePreviousMacro() = runTest {
        val vm = MacroListViewModel(macroRepository)

        vm.moveUp(macros[2])

        verify(macroRepository).reorder(listOf(1L, 3L, 2L))
    }

    @Test
    fun moveDownSwapsWithTheNextMacro() = runTest {
        val vm = MacroListViewModel(macroRepository)

        vm.moveDown(macros[0])

        verify(macroRepository).reorder(listOf(2L, 1L, 3L))
    }

    @Test
    fun movingPastEitherEndDoesNothing() = runTest {
        val vm = MacroListViewModel(macroRepository)

        vm.moveUp(macros[0])
        vm.moveDown(macros[2])

        verify(macroRepository, never()).reorder(any())
    }

    @Test
    fun deleteRemovesTheMacroAndDismissesTheDialog() = runTest {
        val vm = MacroListViewModel(macroRepository)
        vm.showDeleteDialog(macros[1])
        assertThat(vm.uiState.value.showDeleteDialog).isNotNull()

        vm.deleteMacro(macros[1])

        verify(macroRepository).delete(2L)
        assertThat(vm.uiState.value.showDeleteDialog).isNull()
    }

    @Test
    fun hideDeleteDialogClearsItWithoutDeleting() = runTest {
        val vm = MacroListViewModel(macroRepository)
        vm.showDeleteDialog(macros[1])

        vm.hideDeleteDialog()

        assertThat(vm.uiState.value.showDeleteDialog).isNull()
        verify(macroRepository, never()).delete(any())
    }
}
