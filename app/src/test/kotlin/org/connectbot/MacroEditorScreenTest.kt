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

package org.connectbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroStepKind
import org.connectbot.ui.screens.macros.MacroEditorScreenContent
import org.connectbot.ui.screens.macros.MacroEditorTestTags
import org.connectbot.ui.screens.macros.MacroEditorUiState
import org.connectbot.ui.screens.macros.MacroRecorderTestTags
import org.connectbot.ui.screens.macros.MacroStepDialogTestTags
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MacroEditorScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private var addedSteps = mutableListOf<MacroStep>()
    private var label: String? = null
    private var saved = false

    private fun setContent(uiState: MacroEditorUiState) {
        composeTestRule.setContent {
            ConnectBotTheme {
                MacroEditorScreenContent(
                    uiState = uiState,
                    onNavigateBack = {},
                    onLabelChange = { label = it },
                    onAddStep = { addedSteps += it },
                    onReplaceStep = { _, _ -> },
                    onRemoveStep = {},
                    onMoveStep = { _, _ -> },
                    onSave = { saved = true },
                )
            }
        }
    }

    @Test
    fun newMacroShowsTheCreateTitle() {
        setContent(MacroEditorUiState(isLoading = false))

        val title = composeTestRule.activity.getString(R.string.macro_editor_title_new)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun existingMacroShowsTheEditTitle() {
        setContent(MacroEditorUiState(macroId = 1, isLoading = false))

        val title = composeTestRule.activity.getString(R.string.macro_editor_title_edit)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun typingInTheLabelFieldReportsTheNewValue() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.LABEL_FIELD).performTextInput("detach")

        assertThat(label).isEqualTo("detach")
    }

    @Test
    fun existingStepsAreListedWithTheirSummary() {
        setContent(
            MacroEditorUiState(
                macroId = 1,
                isLoading = false,
                label = "detach",
                steps = listOf(
                    MacroStep.key(MacroKey.A, MacroModifiers.CTRL),
                    MacroStep.text("d"),
                ),
            ),
        )

        val ctrl = composeTestRule.activity.getString(R.string.button_key_ctrl)
        composeTestRule.onNodeWithText("$ctrl+A").assertIsDisplayed()
        composeTestRule.onNodeWithText("\"d\"").assertIsDisplayed()
    }

    @Test
    fun recordButtonOpensTheRecorder() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.RECORD).performClick()

        composeTestRule.onNodeWithTag(MacroRecorderTestTags.SHEET).assertIsDisplayed()
    }

    @Test
    fun recordingCtrlThenAAddsThatStep() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.RECORD).performClick()
        // Tap Ctrl on the real key row, then type "a" on the capture field.
        val ctrl = composeTestRule.activity.getString(R.string.button_key_ctrl)
        composeTestRule.onNodeWithText(ctrl).performClick()
        composeTestRule.onNodeWithTag(MacroRecorderTestTags.CAPTURE).performTextInput("a")
        composeTestRule.onNodeWithTag(MacroRecorderTestTags.DONE).performClick()

        assertThat(addedSteps).hasSize(1)
        assertThat(addedSteps[0].kind).isEqualTo(MacroStepKind.KEY)
        assertThat(addedSteps[0].key).isEqualTo(MacroKey.A)
        assertThat(addedSteps[0].modifiers).isEqualTo(MacroModifiers.CTRL)
    }

    @Test
    fun recordingTheTmuxDetachSequenceAddsBothSteps() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.RECORD).performClick()
        val ctrl = composeTestRule.activity.getString(R.string.button_key_ctrl)
        composeTestRule.onNodeWithText(ctrl).performClick()
        composeTestRule.onNodeWithTag(MacroRecorderTestTags.CAPTURE).performTextInput("a")
        composeTestRule.onNodeWithTag(MacroRecorderTestTags.CAPTURE).performTextInput("d")
        composeTestRule.onNodeWithTag(MacroRecorderTestTags.DONE).performClick()

        assertThat(addedSteps.map { it.kind })
            .containsExactly(MacroStepKind.KEY, MacroStepKind.TEXT)
        assertThat(addedSteps[0].key).isEqualTo(MacroKey.A)
        assertThat(addedSteps[1].text).isEqualTo("d")
    }

    @Test
    fun doneIsDisabledUntilSomethingIsRecorded() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.RECORD).performClick()

        composeTestRule.onNodeWithTag(MacroRecorderTestTags.DONE).assertIsNotEnabled()
    }

    @Test
    fun textDialogAddsALiteralTextStep() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.ADD_TEXT).performClick()
        composeTestRule.onNodeWithTag(MacroStepDialogTestTags.TEXT_FIELD).performTextInput("ls -la")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.button_add))
            .performClick()

        assertThat(addedSteps).hasSize(1)
        assertThat(addedSteps[0].kind).isEqualTo(MacroStepKind.TEXT)
        assertThat(addedSteps[0].text).isEqualTo("ls -la")
    }

    @Test
    fun saveActionInvokesSave() {
        setContent(MacroEditorUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroEditorTestTags.SAVE).performClick()

        assertThat(saved).isTrue()
    }

    @Test
    fun validationErrorsAreSurfaced() {
        setContent(
            MacroEditorUiState(isLoading = false, labelError = true, stepsError = true),
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.macro_editor_label_required))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.macro_editor_steps_required))
            .assertIsDisplayed()
    }
}
