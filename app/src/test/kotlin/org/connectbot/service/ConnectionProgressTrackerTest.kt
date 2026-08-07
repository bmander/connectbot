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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProgressTrackerTest {

    private var now = 1_000L
    private val tracker = ConnectionProgressTracker { now }

    private val progress get() = tracker.progress.value

    @Test
    fun beforeBegin_progressIsNull() {
        assertNull(progress)
    }

    @Test
    fun begin_startsAtFirstStage() {
        tracker.begin()

        assertEquals(ConnectionStage.RESOLVING, progress?.stage)
        assertEquals(1_000L, progress?.startedAtMillis)
        assertEquals(1_000L, progress?.stageStartedAtMillis)
        assertNull(progress?.outcome)
    }

    @Test
    fun begin_incrementsAttemptId() {
        val first = tracker.begin()
        val second = tracker.begin()

        assertTrue(second > first)
        assertEquals(second, tracker.attemptId)
    }

    @Test
    fun enter_advancesStageAndRestartsStageClock() {
        tracker.begin()
        now = 3_000L

        tracker.enter(ConnectionStage.HANDSHAKING)

        assertEquals(ConnectionStage.HANDSHAKING, progress?.stage)
        assertEquals(1_000L, progress?.startedAtMillis)
        assertEquals(3_000L, progress?.stageStartedAtMillis)
    }

    @Test
    fun enter_carriesDetail() {
        tracker.begin()

        tracker.enter(ConnectionStage.AUTHENTICATING, "publickey")

        assertEquals("publickey", progress?.detail)
    }

    // Transports report opportunistically and the auth loop can revisit an earlier
    // phase; what the user sees should still only move forward.

    @Test
    fun enter_earlierStageIsIgnored() {
        tracker.begin()
        tracker.enter(ConnectionStage.AUTHENTICATING)

        tracker.enter(ConnectionStage.HANDSHAKING)

        assertEquals(ConnectionStage.AUTHENTICATING, progress?.stage)
    }

    @Test
    fun enter_sameStageUpdatesDetailWithoutRestartingClock() {
        tracker.begin()
        tracker.enter(ConnectionStage.AUTHENTICATING, "publickey")
        now = 9_000L

        tracker.enter(ConnectionStage.AUTHENTICATING, "password")

        assertEquals("password", progress?.detail)
        assertEquals(1_000L, progress?.stageStartedAtMillis)
    }

    @Test
    fun completedStages_derivedFromOrdinal() {
        tracker.begin()
        tracker.enter(ConnectionStage.VERIFYING_HOST_KEY)

        assertEquals(
            listOf(ConnectionStage.RESOLVING, ConnectionStage.HANDSHAKING),
            progress?.completedStages(),
        )
    }

    @Test
    fun detail_replacesTheCurrentDetail() {
        tracker.begin()

        tracker.detail("Connecting to example.com:22 via ssh")
        assertEquals("Connecting to example.com:22 via ssh", progress?.detail)

        tracker.detail("Key exchange algorithm: curve25519-sha256")
        assertEquals("Key exchange algorithm: curve25519-sha256", progress?.detail)
    }

    // The connect log feeds the detail line and stage reports arrive independently,
    // so a transport re-reporting the stage it is already on must not wipe it.

    @Test
    fun enter_sameStageWithoutDetail_keepsExistingDetail() {
        tracker.begin()
        tracker.detail("Looking up example.com")

        tracker.enter(ConnectionStage.RESOLVING)

        assertEquals("Looking up example.com", progress?.detail)
    }

    // Advancing does drop it: the detail described the stage being left.

    @Test
    fun enter_nextStage_clearsDetail() {
        tracker.begin()
        tracker.detail("Looking up example.com")

        tracker.enter(ConnectionStage.HANDSHAKING)

        assertNull(progress?.detail)
    }

    @Test
    fun detail_afterFinish_isIgnored() {
        tracker.begin()
        tracker.cancel()

        tracker.detail("too late")

        assertNull(progress?.detail)
    }

    @Test
    fun setWaitingOnUser_togglesFlag() {
        tracker.begin()

        tracker.setWaitingOnUser(true)
        assertTrue(progress?.waitingOnUser == true)

        tracker.setWaitingOnUser(false)
        assertFalse(progress?.waitingOnUser == true)
    }

    @Test
    fun succeed_marksFinished() {
        tracker.begin()

        tracker.succeed()

        assertEquals(ConnectionOutcome.Succeeded, progress?.outcome)
        assertTrue(progress?.isFinished == true)
    }

    @Test
    fun timeOut_carriesStageAndBudget() {
        tracker.begin()
        tracker.enter(ConnectionStage.HANDSHAKING)

        tracker.timeOut(ConnectionStage.HANDSHAKING, 30_000L)

        assertEquals(
            ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L),
            progress?.outcome,
        )
    }

    @Test
    fun fail_carriesTransportMessage() {
        tracker.begin()

        tracker.fail(ConnectionStage.AUTHENTICATING, "Auth fail")

        assertEquals(
            ConnectionOutcome.Failed(ConnectionStage.AUTHENTICATING, "Auth fail"),
            progress?.outcome,
        )
    }

    // The first outcome to land is the one the user is shown. A timeout that fires
    // while teardown is already reporting a failure must not overwrite it, or the
    // reason shown would depend on a race.

    @Test
    fun outcomesAreSticky() {
        tracker.begin()
        tracker.cancel()

        tracker.timeOut(ConnectionStage.HANDSHAKING, 30_000L)
        tracker.succeed()

        assertEquals(ConnectionOutcome.Cancelled, progress?.outcome)
    }

    @Test
    fun enter_afterFinish_isIgnored() {
        tracker.begin()
        tracker.cancel()

        tracker.enter(ConnectionStage.OPENING_SESSION)

        assertEquals(ConnectionStage.RESOLVING, progress?.stage)
    }

    @Test
    fun finish_clearsWaitingOnUser() {
        tracker.begin()
        tracker.setWaitingOnUser(true)

        tracker.cancel()

        assertFalse(progress?.waitingOnUser == true)
    }

    @Test
    fun begin_afterFinish_startsCleanAttempt() {
        tracker.begin()
        tracker.fail(ConnectionStage.HANDSHAKING, "nope")

        tracker.begin()

        assertNull(progress?.outcome)
        assertEquals(ConnectionStage.RESOLVING, progress?.stage)
    }

    @Test
    fun clear_hidesProgress() {
        tracker.begin()

        tracker.clear()

        assertNull(progress)
    }
}
