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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.Macro
import org.connectbot.data.entity.MacroKey
import org.connectbot.data.entity.MacroModifiers
import org.connectbot.data.entity.MacroStep
import org.connectbot.data.entity.MacroWithSteps
import org.connectbot.ui.screens.macros.MacroListScreenContent
import org.connectbot.ui.screens.macros.MacroListTestTags
import org.connectbot.ui.screens.macros.MacroListUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MacroListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val detach = MacroWithSteps(
        macro = Macro(id = 1, label = "detach", position = 0),
        stepsUnordered = listOf(
            MacroStep.key(MacroKey.A, MacroModifiers.CTRL).copy(position = 0),
            MacroStep.text("d").copy(position = 1),
        ),
    )

    private fun setContent(
        uiState: MacroListUiState,
        onNavigateBack: () -> Unit = {},
        onCreate: () -> Unit = {},
        onEdit: (MacroWithSteps) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                Content(uiState, onNavigateBack, onCreate, onEdit)
            }
        }
    }

    @Composable
    private fun Content(
        uiState: MacroListUiState,
        onNavigateBack: () -> Unit,
        onCreate: () -> Unit,
        onEdit: (MacroWithSteps) -> Unit,
    ) {
        MacroListScreenContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onCreate = onCreate,
            onEdit = onEdit,
            onMoveUp = {},
            onMoveDown = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onDismissDelete = {},
        )
    }

    @Test
    fun emptyStateIsShownWhenThereAreNoMacros() {
        setContent(MacroListUiState(isLoading = false))

        composeTestRule.onNodeWithTag(MacroListTestTags.EMPTY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MacroListTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun macroRowShowsLabelAndStepSummary() {
        setContent(MacroListUiState(isLoading = false, macros = listOf(detach)))

        composeTestRule.onNodeWithText("detach").assertIsDisplayed()
        // The summary renders modifiers and the literal text so the row is self-describing.
        val ctrl = composeTestRule.activity.getString(R.string.button_key_ctrl)
        composeTestRule.onNodeWithText("$ctrl+A · \"d\"").assertIsDisplayed()
    }

    @Test
    fun tappingAMacroOpensItForEditing() {
        var edited: MacroWithSteps? = null
        setContent(MacroListUiState(isLoading = false, macros = listOf(detach)), onEdit = { edited = it })

        composeTestRule.onNodeWithText("detach").performClick()

        assertThat(edited?.macro?.id).isEqualTo(1L)
    }

    @Test
    fun addButtonInvokesCreate() {
        var created = false
        setContent(MacroListUiState(isLoading = false), onCreate = { created = true })

        composeTestRule.onNodeWithTag(MacroListTestTags.ADD).performClick()

        assertThat(created).isTrue()
    }

    @Test
    fun backButtonNavigatesBack() {
        var backCalled = false
        setContent(MacroListUiState(isLoading = false), onNavigateBack = { backCalled = true })

        val navigateUp = composeTestRule.activity.getString(R.string.button_navigate_up)
        composeTestRule.onNodeWithContentDescription(navigateUp).performClick()

        assertThat(backCalled).isTrue()
    }

    @Test
    fun deleteDialogNamesTheMacro() {
        setContent(
            MacroListUiState(isLoading = false, macros = listOf(detach), showDeleteDialog = detach),
        )

        val message = composeTestRule.activity
            .getString(R.string.macro_delete_confirm_message, "detach")
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }
}
