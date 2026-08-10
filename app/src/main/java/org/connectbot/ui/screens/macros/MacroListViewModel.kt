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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.MacroRepository
import org.connectbot.data.entity.MacroWithSteps
import javax.inject.Inject

data class MacroListUiState(
    val macros: List<MacroWithSteps> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: MacroWithSteps? = null,
)

@HiltViewModel
class MacroListViewModel @Inject constructor(
    private val macroRepository: MacroRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacroListUiState())
    val uiState: StateFlow<MacroListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            macroRepository.observeAll().collect { macros ->
                _uiState.update { it.copy(macros = macros, isLoading = false) }
            }
        }
    }

    fun showDeleteDialog(macro: MacroWithSteps) {
        _uiState.update { it.copy(showDeleteDialog = macro) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteMacro(macro: MacroWithSteps) {
        viewModelScope.launch {
            macroRepository.delete(macro.macro.id)
            _uiState.update { it.copy(showDeleteDialog = null) }
        }
    }

    /** Swap a macro with the one before it in the on-screen row. */
    fun moveUp(macro: MacroWithSteps) = move(macro, -1)

    /** Swap a macro with the one after it in the on-screen row. */
    fun moveDown(macro: MacroWithSteps) = move(macro, 1)

    private fun move(macro: MacroWithSteps, delta: Int) {
        val macros = _uiState.value.macros
        val from = macros.indexOfFirst { it.macro.id == macro.macro.id }
        val to = from + delta
        if (from < 0 || to !in macros.indices) return

        // Reorder locally and persist the whole ordering; the observed flow then feeds
        // the new positions back into the UI.
        val ids = macros.map { it.macro.id }.toMutableList()
        ids[from] = ids[to].also { ids[to] = ids[from] }
        viewModelScope.launch { macroRepository.reorder(ids) }
    }
}
