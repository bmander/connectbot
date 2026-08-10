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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.MacroStep

object MacroStepDialogTestTags {
    const val TEXT_DIALOG = "macro_text_dialog"
    const val TEXT_FIELD = "macro_text_field"
}

/**
 * Dialog for a literal-text step.
 *
 * The field is single-line on purpose: a newline here would send LF, whereas pressing
 * Enter in a terminal sends CR. Users add an explicit Enter key step instead, which the
 * hint below the field explains.
 */
@Composable
fun MacroTextStepDialog(
    initial: MacroStep?,
    onDismiss: () -> Unit,
    onConfirm: (MacroStep) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MacroStepDialogTestTags.TEXT_DIALOG),
        title = { Text(stringResource(R.string.macro_text_step_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.macro_text_step_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MacroStepDialogTestTags.TEXT_FIELD),
                )
                Text(
                    text = stringResource(R.string.macro_text_step_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotEmpty(),
                onClick = { onConfirm(MacroStep.text(text)) },
            ) {
                Text(stringResource(R.string.button_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        },
    )
}
