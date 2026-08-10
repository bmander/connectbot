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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind
import org.connectbot.ui.theme.ConnectBotTheme

object MacroEditorTestTags {
    const val LABEL_FIELD = "macro_editor_label"
    const val RECORD = "macro_editor_record"
    const val ADD_TEXT = "macro_editor_add_text"
    const val SAVE = "macro_editor_save"
    const val STEPS = "macro_editor_steps"
}

@Composable
fun MacroEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MacroEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) currentOnNavigateBack()
    }

    MacroEditorScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onLabelChange = viewModel::updateLabel,
        onAddStep = viewModel::addStep,
        onReplaceStep = viewModel::replaceStep,
        onRemoveStep = viewModel::removeStep,
        onMoveStep = viewModel::moveStep,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

/**
 * Stateless UI for the macro editor, separated from [MacroEditorScreen] so it can be
 * driven from tests and previews without a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MacroEditorScreenContent(
    uiState: MacroEditorUiState,
    onNavigateBack: () -> Unit,
    onLabelChange: (String) -> Unit,
    onAddStep: (MacroStep) -> Unit,
    onReplaceStep: (Int, MacroStep) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null means no dialog; an index means "editing that existing text step".
    var textDialogIndex by remember { mutableStateOf<Int?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showRecorder by remember { mutableStateOf(false) }

    fun closeDialogs() {
        showTextDialog = false
        showRecorder = false
        textDialogIndex = null
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.macro_editor_title_new
                            } else {
                                R.string.macro_editor_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave, modifier = Modifier.testTag(MacroEditorTestTags.SAVE)) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.profile_editor_save),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag(MacroEditorTestTags.STEPS),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.label,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.macro_editor_label)) },
                    singleLine = true,
                    isError = uiState.labelError,
                    supportingText = if (uiState.labelError) {
                        { Text(stringResource(R.string.macro_editor_label_required)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(MacroEditorTestTags.LABEL_FIELD),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.macro_editor_steps),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (uiState.stepsError) {
                item {
                    Text(
                        text = stringResource(R.string.macro_editor_steps_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            itemsIndexed(uiState.steps) { index, step ->
                MacroStepRow(
                    step = step,
                    isFirst = index == 0,
                    isLast = index == uiState.steps.lastIndex,
                    // Key steps are re-recorded rather than edited in place; only text
                    // steps get a dialog, since retyping a long command is tedious.
                    onClick = {
                        if (step.kind == MacroStepKind.TEXT) {
                            textDialogIndex = index
                            showTextDialog = true
                        }
                    },
                    onMoveUp = { onMoveStep(index, -1) },
                    onMoveDown = { onMoveStep(index, 1) },
                    onRemove = { onRemoveStep(index) },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showRecorder = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(MacroEditorTestTags.RECORD),
                    ) {
                        Text(stringResource(R.string.macro_editor_record))
                    }
                    OutlinedButton(
                        onClick = {
                            textDialogIndex = null
                            showTextDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(MacroEditorTestTags.ADD_TEXT),
                    ) {
                        Text(stringResource(R.string.macro_editor_add_text))
                    }
                }
            }
        }
    }

    if (showRecorder) {
        MacroRecorderDialog(
            onDismiss = ::closeDialogs,
            onConfirm = { recorded ->
                recorded.forEach(onAddStep)
                closeDialogs()
            },
        )
    }

    if (showTextDialog) {
        val index = textDialogIndex
        MacroTextStepDialog(
            initial = index?.let { uiState.steps.getOrNull(it) },
            onDismiss = ::closeDialogs,
            onConfirm = { step ->
                if (index == null) onAddStep(step) else onReplaceStep(index, step)
                closeDialogs()
            },
        )
    }
}

@Composable
private fun MacroStepRow(
    step: MacroStep,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = macroStepSummary(context, step),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.macro_editor_move_step_up),
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.macro_editor_move_step_down),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.macro_editor_remove_step),
                )
            }
        }
    }
}

@Preview(name = "Macro Editor", showBackground = true)
@Composable
private fun MacroEditorScreenPreview() {
    ConnectBotTheme {
        MacroEditorScreenContent(
            uiState = MacroEditorUiState(
                macroId = 1,
                label = "detach",
                isLoading = false,
                steps = listOf(
                    MacroStep.key(MacroKey.A, MacroModifiers.CTRL),
                    MacroStep.text("d"),
                ),
            ),
            onNavigateBack = {},
            onLabelChange = {},
            onAddStep = {},
            onReplaceStep = { _, _ -> },
            onRemoveStep = {},
            onMoveStep = { _, _ -> },
            onSave = {},
        )
    }
}
