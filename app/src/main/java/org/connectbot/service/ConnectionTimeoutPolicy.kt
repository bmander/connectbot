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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.connectbot.util.PreferenceConstants

/**
 * How long each connection phase may take before the attempt is abandoned.
 *
 * A budget of null means "no deadline".
 */
data class ConnectionTimeouts(
    val resolve: Long?,
    val handshake: Long?,
    val verifyHostKey: Long?,
    val auth: Long?,
    val session: Long?,
) {
    companion object {
        /** Sentinel for the "Never" preference option. */
        const val DISABLED = 0L

        /** Derived from the shipped preference default, so the two cannot drift. */
        val DEFAULT = fromBaseMillis(PreferenceConstants.DEFAULT_CONNECT_TIMEOUT.toLong() * 1000L)

        val NONE = ConnectionTimeouts(null, null, null, null, null)

        /**
         * Derive all five budgets from the single user-facing value.
         *
         * Resolution gets half — DNS that has not answered in that long is not going
         * to. Authentication gets double, because it can involve several round trips
         * and a retry loop. A base of [DISABLED] restores the unbounded behaviour
         * that predates this feature, which is the escape hatch for anyone on a link
         * so slow that the defaults get in the way.
         */
        fun fromBaseMillis(base: Long): ConnectionTimeouts {
            if (base <= DISABLED) return NONE
            return ConnectionTimeouts(
                resolve = base / 2,
                handshake = base,
                verifyHostKey = base,
                auth = base * 2,
                session = base,
            )
        }
    }
}

object ConnectionTimeoutPolicy {
    /**
     * The budget for [stage], or null if it should not be timed out.
     *
     * Returns null whenever [waitingOnUser] holds. This is the rule that keeps a
     * password prompt from being killed out from under someone who is still typing —
     * the clock stops while a human is being waited on, and [watchConnection]
     * restarts it once they answer.
     */
    fun budgetFor(
        stage: ConnectionStage,
        timeouts: ConnectionTimeouts,
        waitingOnUser: Boolean,
    ): Long? {
        if (waitingOnUser) return null
        return when (stage) {
            ConnectionStage.RESOLVING -> timeouts.resolve
            ConnectionStage.HANDSHAKING -> timeouts.handshake
            ConnectionStage.VERIFYING_HOST_KEY -> timeouts.verifyHostKey
            ConnectionStage.AUTHENTICATING -> timeouts.auth
            ConnectionStage.OPENING_SESSION -> timeouts.session
        }
    }
}

/** Just the fields the watchdog reacts to, so unrelated changes cannot disturb it. */
private data class WatchedState(
    val stage: ConnectionStage,
    val waitingOnUser: Boolean,
    val isFinished: Boolean,
)

/**
 * Watch [progress] and invoke [onTimeout] when a stage overruns its budget.
 *
 * Built on [collectLatest], which cancels the pending delay whenever a new value
 * arrives. That gives the re-arming behaviour for free: advancing a stage restarts
 * the clock, entering a prompt disarms it, and answering the prompt starts it again
 * from full — no timer bookkeeping, and nothing to leak if the caller is cancelled.
 *
 * The snapshot is narrowed to [WatchedState] first, which matters rather than being
 * tidiness: every line the connect log mirrors into the progress detail produces a
 * new snapshot, and collecting those directly would restart the deadline on each
 * one — so a stage that kept chattering could never time out at all.
 *
 * A finished attempt is passed through rather than completing the flow: on upstream
 * completion collectLatest *waits* for the in-flight action instead of cancelling it,
 * so ending the stream would let the pending delay run out and fire a timeout for an
 * attempt that had already succeeded.
 *
 * Suspends until the caller is cancelled.
 */
suspend fun watchConnection(
    progress: Flow<ConnectionProgress?>,
    timeouts: ConnectionTimeouts,
    onTimeout: (ConnectionStage, Long) -> Unit,
) {
    progress
        .map { it?.let { s -> WatchedState(s.stage, s.waitingOnUser, s.isFinished) } }
        .distinctUntilChanged()
        .collectLatest { watched ->
            if (watched == null || watched.isFinished) return@collectLatest
            val budget = ConnectionTimeoutPolicy.budgetFor(
                watched.stage,
                timeouts,
                watched.waitingOnUser,
            ) ?: return@collectLatest
            delay(budget)
            onTimeout(watched.stage, budget)
        }
}
