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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.MacroRepository
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroStep
import org.connectbot.ui.navigation.NavArgs
import javax.inject.Inject

/** Macro ID used by the editor route to mean "create a new macro". */
const val NEW_MACRO_ID = -1L

data class MacroEditorUiState(
    val macroId: Long = NEW_MACRO_ID,
    val label: String = "",
    /** Preserved across an edit so saving does not move the button in the row. */
    val position: Int = 0,
    val steps: List<MacroStep> = emptyList(),
    val isLoading: Boolean = true,
    val labelError: Boolean = false,
    val stepsError: Boolean = false,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = macroId == NEW_MACRO_ID
}

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val macroRepository: MacroRepository,
) : ViewModel() {

    private val macroId: Long = savedStateHandle.get<Long>(NavArgs.MACRO_ID) ?: NEW_MACRO_ID

    private val _uiState = MutableStateFlow(MacroEditorUiState(macroId = macroId))
    val uiState: StateFlow<MacroEditorUiState> = _uiState.asStateFlow()

    init {
        if (macroId == NEW_MACRO_ID) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val existing = macroRepository.getById(macroId)
                _uiState.update {
                    it.copy(
                        label = existing?.macro?.label.orEmpty(),
                        position = existing?.macro?.position ?: 0,
                        steps = existing?.steps.orEmpty(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun updateLabel(label: String) {
        _uiState.update { it.copy(label = label, labelError = false) }
    }

    fun addStep(step: MacroStep) {
        _uiState.update { it.copy(steps = it.steps + step, stepsError = false) }
    }

    fun replaceStep(index: Int, step: MacroStep) {
        _uiState.update { state ->
            if (index !in state.steps.indices) {
                state
            } else {
                state.copy(steps = state.steps.toMutableList().also { it[index] = step })
            }
        }
    }

    fun removeStep(index: Int) {
        _uiState.update { state ->
            if (index !in state.steps.indices) {
                state
            } else {
                state.copy(steps = state.steps.filterIndexed { i, _ -> i != index })
            }
        }
    }

    /** Move a step by [delta] places; a move off either end is ignored. */
    fun moveStep(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            if (index !in state.steps.indices || target !in state.steps.indices) {
                state
            } else {
                val steps = state.steps.toMutableList()
                steps[index] = steps[target].also { steps[target] = steps[index] }
                state.copy(steps = steps)
            }
        }
    }

    /**
     * Validate and persist. Positions are assigned by the DAO from list order, so the
     * in-memory list is the single source of truth for ordering.
     */
    fun save() {
        val state = _uiState.value
        val labelError = state.label.isBlank()
        val stepsError = state.steps.isEmpty()
        if (labelError || stepsError) {
            _uiState.update { it.copy(labelError = labelError, stepsError = stepsError) }
            return
        }

        viewModelScope.launch {
            val macro = Macro(
                id = if (state.isNew) 0L else state.macroId,
                label = state.label.trim(),
                position = state.position,
            )
            macroRepository.save(macro, state.steps)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
