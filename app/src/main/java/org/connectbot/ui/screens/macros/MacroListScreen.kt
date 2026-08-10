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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps
import org.connectbot.ui.theme.ConnectBotTheme

object MacroListTestTags {
    const val ADD = "macro_list_add"
    const val EMPTY = "macro_list_empty"
    const val LIST = "macro_list"
}

@Composable
fun MacroListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MacroListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MacroListScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onCreate = { onNavigateToEdit(NEW_MACRO_ID) },
        onEdit = { onNavigateToEdit(it.macro.id) },
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onRequestDelete = viewModel::showDeleteDialog,
        onConfirmDelete = viewModel::deleteMacro,
        onDismissDelete = viewModel::hideDeleteDialog,
        modifier = modifier,
    )
}

/**
 * Stateless UI for the macro list, separated from [MacroListScreen] so it can be driven
 * from tests and previews without a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MacroListScreenContent(
    uiState: MacroListUiState,
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (MacroWithSteps) -> Unit,
    onMoveUp: (MacroWithSteps) -> Unit,
    onMoveDown: (MacroWithSteps) -> Unit,
    onRequestDelete: (MacroWithSteps) -> Unit,
    onConfirmDelete: (MacroWithSteps) -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.macro_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.testTag(MacroListTestTags.ADD),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.macro_list_add),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.macros.isEmpty() -> Text(
                    text = stringResource(R.string.macro_list_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .testTag(MacroListTestTags.EMPTY),
                    style = MaterialTheme.typography.bodyLarge,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MacroListTestTags.LIST),
                ) {
                    items(uiState.macros, key = { it.macro.id }) { macro ->
                        MacroListItem(
                            macro = macro,
                            isFirst = macro == uiState.macros.first(),
                            isLast = macro == uiState.macros.last(),
                            onClick = { onEdit(macro) },
                            onMoveUp = { onMoveUp(macro) },
                            onMoveDown = { onMoveDown(macro) },
                            onDelete = { onRequestDelete(macro) },
                        )
                    }
                }
            }
        }
    }

    uiState.showDeleteDialog?.let { macro ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.macro_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.macro_delete_confirm_message, macro.macro.label))
            },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(macro) }) {
                    Text(stringResource(R.string.delete_pos))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.delete_neg))
                }
            },
        )
    }
}

@Composable
private fun MacroListItem(
    macro: MacroWithSteps,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val summary = remember(macro) { macroSummary(context, macro.steps) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = macro.macro.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.profile_list_more_options),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.macro_list_move_up)) },
                        enabled = !isFirst,
                        onClick = {
                            showMenu = false
                            onMoveUp()
                        },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.macro_list_move_down)) },
                        enabled = !isLast,
                        onClick = {
                            showMenu = false
                            onMoveDown()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_list_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Preview(name = "Macro List", showBackground = true)
@Composable
private fun MacroListScreenPreview() {
    ConnectBotTheme {
        MacroListScreenContent(
            uiState = MacroListUiState(
                isLoading = false,
                macros = listOf(
                    MacroWithSteps(
                        macro = Macro(id = 1, label = "detach", position = 0),
                        stepsUnordered = listOf(
                            MacroStep.key(MacroKey.A, MacroModifiers.CTRL),
                            MacroStep.text("d"),
                        ),
                    ),
                    MacroWithSteps(
                        macro = Macro(id = 2, label = "^C", position = 1),
                        stepsUnordered = listOf(MacroStep.key(MacroKey.C, MacroModifiers.CTRL)),
                    ),
                ),
            ),
            onNavigateBack = {},
            onCreate = {},
            onEdit = {},
            onMoveUp = {},
            onMoveDown = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onDismissDelete = {},
        )
    }
}

@Preview(name = "Macro List - Empty", showBackground = true)
@Composable
private fun MacroListScreenEmptyPreview() {
    ConnectBotTheme {
        MacroListScreenContent(
            uiState = MacroListUiState(isLoading = false),
            onNavigateBack = {},
            onCreate = {},
            onEdit = {},
            onMoveUp = {},
            onMoveDown = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onDismissDelete = {},
        )
    }
}
