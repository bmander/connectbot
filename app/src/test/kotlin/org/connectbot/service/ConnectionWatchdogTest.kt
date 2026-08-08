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

package org.connectbot.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [watchConnection] against virtual time. The re-arm cases in particular
 * are not observable any other way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionWatchdogTest {

    private val timeouts = ConnectionTimeouts.fromBaseMillis(30_000L)

    private fun snapshot(
        stage: ConnectionStage,
        waitingOnUser: Boolean = false,
        outcome: ConnectionOutcome? = null,
    ) = ConnectionProgress(
        stage = stage,
        stageStartedAtMillis = 0L,
        waitingOnUser = waitingOnUser,
        outcome = outcome,
    )

    @Test
    fun firesWhenStageOverrunsItsBudget() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(snapshot(ConnectionStage.HANDSHAKING))
        val fired = mutableListOf<Pair<ConnectionStage, Long>>()

        val job = launch { watchConnection(progress, timeouts) { s, b -> fired += s to b } }

        advanceTimeBy(29_999)
        runCurrent()
        assertTrue("should not have fired before the budget", fired.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(ConnectionStage.HANDSHAKING to 30_000L), fired)

        job.cancel()
    }

    @Test
    fun doesNotFireWhenStageAdvancesFirst() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(snapshot(ConnectionStage.HANDSHAKING))
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        advanceTimeBy(20_000)
        runCurrent()
        progress.value = snapshot(ConnectionStage.AUTHENTICATING)

        // The old stage's remaining 10s must not fire; the new stage restarts from full.
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(fired.isEmpty())

        advanceTimeBy(40_001)
        runCurrent()
        assertEquals(listOf(ConnectionStage.AUTHENTICATING), fired)

        job.cancel()
    }

    @Test
    fun doesNotFireWhileWaitingOnUser() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(
            snapshot(ConnectionStage.AUTHENTICATING, waitingOnUser = true),
        )
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        advanceTimeBy(600_000)
        runCurrent()

        assertTrue("a user typing a password must never be timed out", fired.isEmpty())
        job.cancel()
    }

    // The case most likely to regress: the clock has to start again from full once
    // the prompt is answered, not resume from wherever it was before.

    @Test
    fun reArmsAfterUserAnswers() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(
            snapshot(ConnectionStage.AUTHENTICATING, waitingOnUser = true),
        )
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        advanceTimeBy(600_000)
        runCurrent()
        progress.value = snapshot(ConnectionStage.AUTHENTICATING, waitingOnUser = false)

        advanceTimeBy(59_999)
        runCurrent()
        assertTrue("should get the full budget after answering", fired.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(ConnectionStage.AUTHENTICATING), fired)

        job.cancel()
    }

    @Test
    fun doesNotFireAfterAttemptFinishes() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(snapshot(ConnectionStage.OPENING_SESSION))
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        advanceTimeBy(1_000)
        runCurrent()
        progress.value = snapshot(ConnectionStage.OPENING_SESSION, outcome = ConnectionOutcome.Succeeded)

        advanceTimeBy(600_000)
        runCurrent()

        assertTrue(fired.isEmpty())
        job.cancel()
    }

    // The connect log mirrors every line it prints into the progress detail, so a
    // chatty stage emits constantly. If those emissions re-armed the watchdog the
    // deadline would never be reached and the stage could hang indefinitely.

    @Test
    fun detailChangesDoNotRestartTheClock() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(snapshot(ConnectionStage.HANDSHAKING))
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        repeat(20) { i ->
            advanceTimeBy(1_000)
            runCurrent()
            progress.value = snapshot(ConnectionStage.HANDSHAKING).copy(detail = "log line $i")
        }

        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(listOf(ConnectionStage.HANDSHAKING), fired)
        job.cancel()
    }

    @Test
    fun doesNotFireWhenProgressIsNull() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(null)
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, timeouts) { s, _ -> fired += s } }

        advanceTimeBy(600_000)
        runCurrent()

        assertTrue(fired.isEmpty())
        job.cancel()
    }

    @Test
    fun neverFiresWhenTimeoutsDisabled() = runTest {
        val progress = MutableStateFlow<ConnectionProgress?>(snapshot(ConnectionStage.HANDSHAKING))
        val fired = mutableListOf<ConnectionStage>()

        val job = launch { watchConnection(progress, ConnectionTimeouts.NONE) { s, _ -> fired += s } }

        advanceTimeBy(3_600_000)
        runCurrent()

        assertTrue(fired.isEmpty())
        job.cancel()
    }
}
