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

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.MacroRepository
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps
import org.connectbot.ui.navigation.NavArgs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MacroEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var macroRepository: MacroRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        macroRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(macroId: Long = NEW_MACRO_ID): MacroEditorViewModel {
        val savedStateHandle = mock<SavedStateHandle>()
        whenever(savedStateHandle.get<Long>(NavArgs.MACRO_ID)).thenReturn(macroId)
        return MacroEditorViewModel(savedStateHandle, macroRepository)
    }

    @Test
    fun newMacroStartsEmptyAndNotLoading() {
        val vm = viewModel()

        val state = vm.uiState.value
        assertThat(state.isNew).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.label).isEmpty()
        assertThat(state.steps).isEmpty()
    }

    @Test
    fun existingMacroIsLoadedIntoState() = runTest {
        whenever(macroRepository.getById(7L)).thenReturn(
            MacroWithSteps(
                macro = Macro(id = 7, label = "detach", position = 3),
                stepsUnordered = listOf(
                    MacroStep.key(MacroKey.A, MacroModifiers.CTRL).copy(position = 0),
                    MacroStep.text("d").copy(position = 1),
                ),
            ),
        )

        val state = viewModel(macroId = 7L).uiState.value

        assertThat(state.isNew).isFalse()
        assertThat(state.label).isEqualTo("detach")
        assertThat(state.position).isEqualTo(3)
        assertThat(state.steps).hasSize(2)
    }

    @Test
    fun blankLabelBlocksSave() = runTest {
        val vm = viewModel()
        vm.addStep(MacroStep.key(MacroKey.A))

        vm.save()

        assertThat(vm.uiState.value.labelError).isTrue()
        assertThat(vm.uiState.value.saved).isFalse()
        verify(macroRepository, never()).save(any(), any())
    }

    @Test
    fun emptyStepListBlocksSave() = runTest {
        val vm = viewModel()
        vm.updateLabel("detach")

        vm.save()

        assertThat(vm.uiState.value.stepsError).isTrue()
        assertThat(vm.uiState.value.saved).isFalse()
        verify(macroRepository, never()).save(any(), any())
    }

    @Test
    fun labelErrorClearsWhenTheLabelIsEdited() = runTest {
        val vm = viewModel()
        vm.addStep(MacroStep.key(MacroKey.A))
        vm.save()
        assertThat(vm.uiState.value.labelError).isTrue()

        vm.updateLabel("d")

        assertThat(vm.uiState.value.labelError).isFalse()
    }

    @Test
    fun stepEditsHappenInMemoryWithoutTouchingTheRepository() = runTest {
        val vm = viewModel()

        vm.addStep(MacroStep.key(MacroKey.A))
        vm.addStep(MacroStep.key(MacroKey.B))
        vm.moveStep(0, 1)
        vm.removeStep(1)

        assertThat(vm.uiState.value.steps.map { it.key }).containsExactly(MacroKey.B)
        verify(macroRepository, never()).save(any(), any())
    }

    @Test
    fun movingAStepOffEitherEndIsIgnored() {
        val vm = viewModel()
        vm.addStep(MacroStep.key(MacroKey.A))
        vm.addStep(MacroStep.key(MacroKey.B))

        vm.moveStep(0, -1)
        vm.moveStep(1, 1)

        assertThat(vm.uiState.value.steps.map { it.key })
            .containsExactly(MacroKey.A, MacroKey.B)
    }

    @Test
    fun replaceStepSwapsInPlace() {
        val vm = viewModel()
        vm.addStep(MacroStep.key(MacroKey.A))
        vm.addStep(MacroStep.key(MacroKey.B))

        vm.replaceStep(1, MacroStep.key(MacroKey.Z, MacroModifiers.CTRL))

        val steps = vm.uiState.value.steps
        assertThat(steps.map { it.key }).containsExactly(MacroKey.A, MacroKey.Z)
        assertThat(steps[1].modifiers).isEqualTo(MacroModifiers.CTRL)
    }

    @Test
    fun savingANewMacroPersistsTrimmedLabelAndStepsInOrder() = runTest {
        val vm = viewModel()
        vm.updateLabel("  detach  ")
        vm.addStep(MacroStep.key(MacroKey.A, MacroModifiers.CTRL))
        vm.addStep(MacroStep.text("d"))

        vm.save()

        val macroCaptor = argumentCaptor<Macro>()
        val stepsCaptor = argumentCaptor<List<MacroStep>>()
        verify(macroRepository).save(macroCaptor.capture(), stepsCaptor.capture())
        assertThat(macroCaptor.firstValue.id).isEqualTo(0L)
        assertThat(macroCaptor.firstValue.label).isEqualTo("detach")
        assertThat(stepsCaptor.firstValue.map { it.key }).containsExactly(MacroKey.A, null)
        assertThat(vm.uiState.value.saved).isTrue()
    }

    /**
     * Saving an edit must not move the button in the on-screen row, so the loaded
     * position has to survive the round trip.
     */
    @Test
    fun savingAnEditPreservesTheRowPosition() = runTest {
        whenever(macroRepository.getById(7L)).thenReturn(
            MacroWithSteps(
                macro = Macro(id = 7, label = "detach", position = 3),
                stepsUnordered = listOf(MacroStep.key(MacroKey.A, MacroModifiers.CTRL)),
            ),
        )
        val vm = viewModel(macroId = 7L)

        vm.updateLabel("detach2")
        vm.save()

        val macroCaptor = argumentCaptor<Macro>()
        verify(macroRepository).save(macroCaptor.capture(), any())
        assertThat(macroCaptor.firstValue.id).isEqualTo(7L)
        assertThat(macroCaptor.firstValue.position).isEqualTo(3)
    }
}
