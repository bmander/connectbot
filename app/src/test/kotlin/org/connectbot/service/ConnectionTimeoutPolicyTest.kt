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
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionTimeoutPolicyTest {

    private val timeouts = ConnectionTimeouts.fromBaseMillis(30_000L)

    private fun budget(stage: ConnectionStage, waitingOnUser: Boolean = false) = ConnectionTimeoutPolicy.budgetFor(stage, timeouts, waitingOnUser)

    @Test
    fun fromBaseMillis_derivesAllBudgets() {
        assertEquals(15_000L, timeouts.resolve)
        assertEquals(30_000L, timeouts.handshake)
        assertEquals(30_000L, timeouts.verifyHostKey)
        assertEquals(60_000L, timeouts.auth)
        assertEquals(30_000L, timeouts.session)
    }

    @Test
    fun everyStageHasABudgetByDefault() {
        ConnectionStage.entries.forEach { stage ->
            assertEquals("stage $stage should be bounded", true, budget(stage) != null)
        }
    }

    @Test
    fun budgetFor_matchesTheStage() {
        assertEquals(15_000L, budget(ConnectionStage.RESOLVING))
        assertEquals(30_000L, budget(ConnectionStage.HANDSHAKING))
        assertEquals(30_000L, budget(ConnectionStage.VERIFYING_HOST_KEY))
        assertEquals(60_000L, budget(ConnectionStage.AUTHENTICATING))
        assertEquals(30_000L, budget(ConnectionStage.OPENING_SESSION))
    }

    // Someone typing a password must never be timed out.

    @Test
    fun waitingOnUser_disablesEveryStage() {
        ConnectionStage.entries.forEach { stage ->
            assertNull("stage $stage should not be bounded while prompting", budget(stage, waitingOnUser = true))
        }
    }

    // The "Never" preference restores the unbounded behaviour that predates this
    // feature, and is the escape hatch if the defaults ever get in someone's way.

    @Test
    fun disabledBase_yieldsNoBudgets() {
        val none = ConnectionTimeouts.fromBaseMillis(ConnectionTimeouts.DISABLED)

        ConnectionStage.entries.forEach { stage ->
            assertNull(ConnectionTimeoutPolicy.budgetFor(stage, none, waitingOnUser = false))
        }
    }

    @Test
    fun negativeBase_treatedAsDisabled() {
        assertEquals(ConnectionTimeouts.NONE, ConnectionTimeouts.fromBaseMillis(-1L))
    }

    @Test
    fun default_usesDefaultBase() {
        assertEquals(
            ConnectionTimeouts.fromBaseMillis(ConnectionTimeouts.DEFAULT_BASE_MILLIS),
            ConnectionTimeouts.DEFAULT,
        )
    }
}
