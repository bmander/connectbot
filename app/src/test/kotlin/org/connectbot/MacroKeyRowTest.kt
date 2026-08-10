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
import org.connectbot.ui.components.MACRO_KEY_ROW_TEST_TAG
import org.connectbot.ui.components.MacroKeyRow
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MacroKeyRowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun macro(id: Long, label: String) = MacroWithSteps(
        macro = Macro(id = id, label = label, position = id.toInt()),
        stepsUnordered = listOf(MacroStep.key(MacroKey.C, MacroModifiers.CTRL)),
    )

    @Test
    fun rendersOneButtonPerMacro() {
        composeTestRule.setContent {
            ConnectBotTheme {
                MacroKeyRow(
                    macros = listOf(macro(1, "^C"), macro(2, "detach")),
                    onMacroClick = {},
                    onInteraction = {},
                )
            }
        }

        composeTestRule.onNodeWithText("^C").assertIsDisplayed()
        composeTestRule.onNodeWithText("detach").assertIsDisplayed()
    }

    @Test
    fun clickingAButtonReportsThatMacroAndCountsAsInteraction() {
        var clicked: MacroWithSteps? = null
        var interactions = 0

        composeTestRule.setContent {
            ConnectBotTheme {
                MacroKeyRow(
                    macros = listOf(macro(1, "^C"), macro(2, "detach")),
                    onMacroClick = { clicked = it },
                    onInteraction = { interactions++ },
                )
            }
        }

        composeTestRule.onNodeWithText("detach").performClick()

        assertThat(clicked?.macro?.label).isEqualTo("detach")
        // The auto-hide timer must reset on a macro press, same as any other key.
        assertThat(interactions).isGreaterThan(0)
    }

    @Test
    fun buttonsCarryAnAccessibilityDescription() {
        composeTestRule.setContent {
            ConnectBotTheme {
                MacroKeyRow(macros = listOf(macro(1, "^C")), onMacroClick = {}, onInteraction = {})
            }
        }

        val description = composeTestRule.activity.getString(R.string.macro_button_description, "^C")
        composeTestRule.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun emptyMacroListStillRendersNoButtons() {
        composeTestRule.setContent {
            ConnectBotTheme {
                MacroKeyRow(macros = emptyList(), onMacroClick = {}, onInteraction = {})
            }
        }

        composeTestRule.onNodeWithTag(MACRO_KEY_ROW_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("macro_1").assertDoesNotExist()
    }
}
