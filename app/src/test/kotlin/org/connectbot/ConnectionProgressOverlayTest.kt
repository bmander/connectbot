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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.ConnectionOutcome
import org.connectbot.service.ConnectionProgress
import org.connectbot.service.ConnectionStage
import org.connectbot.ui.components.ConnectionProgressOverlay
import org.connectbot.ui.components.TAG_CONNECTION_PROGRESS_CANCEL
import org.connectbot.ui.components.TAG_CONNECTION_PROGRESS_CLOSE
import org.connectbot.ui.components.TAG_CONNECTION_PROGRESS_OVERLAY
import org.connectbot.ui.components.TAG_CONNECTION_PROGRESS_RETRY
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionProgressOverlayTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun progress(
        stage: ConnectionStage,
        detail: String? = null,
        waitingOnUser: Boolean = false,
        outcome: ConnectionOutcome? = null,
    ) = ConnectionProgress(
        stage = stage,
        startedAtMillis = 0L,
        stageStartedAtMillis = 0L,
        detail = detail,
        waitingOnUser = waitingOnUser,
        outcome = outcome,
    )

    private fun timedOut() = ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L)

    private fun setOverlay(
        value: ConnectionProgress?,
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                ConnectionProgressOverlay(
                    progress = value,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onClose = onClose,
                )
            }
        }
    }

    @Test
    fun hiddenWhenThereIsNoAttempt() {
        setOverlay(null)

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_OVERLAY).assertDoesNotExist()
    }

    @Test
    fun showsEveryStageWhileConnecting() {
        setOverlay(progress(ConnectionStage.HANDSHAKING))

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_OVERLAY).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.connecting_stage_resolving),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.connecting_stage_opening_session),
        ).assertIsDisplayed()
    }

    @Test
    fun showsDetailBesideTheCurrentStage() {
        setOverlay(progress(ConnectionStage.AUTHENTICATING, detail = "publickey"))

        composeTestRule.onNodeWithText("publickey", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsWaitingOnUserInsteadOfDetail() {
        setOverlay(progress(ConnectionStage.AUTHENTICATING, waitingOnUser = true))

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.connecting_waiting_for_you),
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun cancelInvokesCallback() {
        var cancelled = false
        setOverlay(progress(ConnectionStage.HANDSHAKING), onCancel = { cancelled = true })

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CANCEL).performClick()

        assertTrue(cancelled)
    }

    // A finished attempt used to leave the card up with no action on it at all,
    // stranding the user on a dead panel with only the back arrow.

    @Test
    fun failedAttemptOffersRetryAndClose() {
        setOverlay(progress(ConnectionStage.HANDSHAKING, outcome = timedOut()))

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_RETRY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CLOSE).assertIsDisplayed()
    }

    @Test
    fun retryInvokesCallback() {
        var retried = false
        setOverlay(
            progress(ConnectionStage.HANDSHAKING, outcome = timedOut()),
            onRetry = { retried = true },
        )

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_RETRY).performClick()

        assertTrue(retried)
    }

    @Test
    fun closeInvokesCallback() {
        var closed = false
        setOverlay(
            progress(ConnectionStage.HANDSHAKING, outcome = timedOut()),
            onClose = { closed = true },
        )

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CLOSE).performClick()

        assertTrue(closed)
    }

    @Test
    fun cancelledAttemptAlsoOffersRetryAndClose() {
        setOverlay(
            progress(ConnectionStage.HANDSHAKING, outcome = ConnectionOutcome.Cancelled),
        )

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_RETRY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CLOSE).assertIsDisplayed()
    }

    // Retry and Close are the failure actions; while an attempt is still running the
    // only meaningful action is to abandon it.

    @Test
    fun retryAndCloseHiddenWhileConnecting() {
        setOverlay(progress(ConnectionStage.HANDSHAKING))

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_RETRY).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CLOSE).assertDoesNotExist()
    }

    // Once an attempt has ended there is nothing left to cancel, and offering the
    // button would suggest otherwise.

    @Test
    fun cancelHiddenOnceFinished() {
        setOverlay(
            progress(
                ConnectionStage.HANDSHAKING,
                outcome = ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L),
            ),
        )

        composeTestRule.onNodeWithTag(TAG_CONNECTION_PROGRESS_CANCEL).assertDoesNotExist()
    }

    @Test
    fun timeoutReportsHowLongItWaited() {
        setOverlay(
            progress(
                ConnectionStage.HANDSHAKING,
                outcome = ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L),
            ),
        )

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.connecting_timed_out, 30),
        ).assertIsDisplayed()
    }

    @Test
    fun failureShowsTransportMessage() {
        setOverlay(
            progress(
                ConnectionStage.AUTHENTICATING,
                outcome = ConnectionOutcome.Failed(ConnectionStage.AUTHENTICATING, "Auth fail"),
            ),
        )

        composeTestRule.onNodeWithText("Auth fail").assertIsDisplayed()
    }

    @Test
    fun cancellationIsReported() {
        setOverlay(
            progress(ConnectionStage.HANDSHAKING, outcome = ConnectionOutcome.Cancelled),
        )

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.connecting_cancelled),
        ).assertIsDisplayed()
    }
}
