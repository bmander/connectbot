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
        assertEquals(1_000L, progress?.stageStartedAtMillis)
        assertNull(progress?.outcome)
    }

    @Test
    fun enter_advancesStageAndRestartsStageClock() {
        tracker.begin()
        now = 3_000L

        tracker.enter(ConnectionStage.HANDSHAKING)

        assertEquals(ConnectionStage.HANDSHAKING, progress?.stage)
        assertEquals(3_000L, progress?.stageStartedAtMillis)
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
    fun detail_replacesTheCurrentDetail() {
        tracker.begin()

        tracker.detail("Connecting to example.com:22 via ssh")
        assertEquals("Connecting to example.com:22 via ssh", progress?.detail)

        tracker.detail("Key exchange algorithm: curve25519-sha256")
        assertEquals("Key exchange algorithm: curve25519-sha256", progress?.detail)
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
        tracker.record(ConnectionOutcome.Cancelled)

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

        tracker.record(ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L))

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
        tracker.record(ConnectionOutcome.Cancelled)

        tracker.record(ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L))
        tracker.succeed()

        assertEquals(ConnectionOutcome.Cancelled, progress?.outcome)
    }

    // Teardown reports a failure with nothing to say and often wins the race against
    // the transport that actually knows the cause, so the reason has to be able to
    // land afterwards or the card settles on a bare "Connection failed".

    @Test
    fun failureWithoutMessage_yieldsToOneWithAReason() {
        tracker.begin()
        tracker.fail(ConnectionStage.HANDSHAKING, null)

        tracker.fail(ConnectionStage.HANDSHAKING, "The connect() operation timed out.")

        assertEquals(
            ConnectionOutcome.Failed(ConnectionStage.HANDSHAKING, "The connect() operation timed out."),
            progress?.outcome,
        )
    }

    @Test
    fun failureWithoutMessage_yieldsToATimeout() {
        tracker.begin()
        tracker.fail(ConnectionStage.HANDSHAKING, null)

        tracker.record(ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L))

        assertEquals(
            ConnectionOutcome.TimedOut(ConnectionStage.HANDSHAKING, 30_000L),
            progress?.outcome,
        )
    }

    @Test
    fun failureWithAReason_isNotReplaced() {
        tracker.begin()
        tracker.fail(ConnectionStage.HANDSHAKING, "No route to host")

        tracker.fail(ConnectionStage.HANDSHAKING, "something else")
        tracker.fail(ConnectionStage.HANDSHAKING, null)

        assertEquals(
            ConnectionOutcome.Failed(ConnectionStage.HANDSHAKING, "No route to host"),
            progress?.outcome,
        )
    }

    @Test
    fun blankMessage_countsAsNoReason() {
        tracker.begin()
        tracker.fail(ConnectionStage.HANDSHAKING, "   ")

        tracker.fail(ConnectionStage.HANDSHAKING, "No route to host")

        assertEquals(
            ConnectionOutcome.Failed(ConnectionStage.HANDSHAKING, "No route to host"),
            progress?.outcome,
        )
    }

    // A cancel or a success is never displaced, however late a failure arrives.

    @Test
    fun cancellation_isNotReplacedByAFailure() {
        tracker.begin()
        tracker.record(ConnectionOutcome.Cancelled)

        tracker.fail(ConnectionStage.HANDSHAKING, "No route to host")

        assertEquals(ConnectionOutcome.Cancelled, progress?.outcome)
    }

    @Test
    fun enter_afterFinish_isIgnored() {
        tracker.begin()
        tracker.record(ConnectionOutcome.Cancelled)

        tracker.enter(ConnectionStage.OPENING_SESSION)

        assertEquals(ConnectionStage.RESOLVING, progress?.stage)
    }

    @Test
    fun finish_clearsWaitingOnUser() {
        tracker.begin()
        tracker.setWaitingOnUser(true)

        tracker.record(ConnectionOutcome.Cancelled)

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
}
