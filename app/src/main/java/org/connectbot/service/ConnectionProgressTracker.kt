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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks a single connection attempt and publishes it as observable state.
 *
 * Transports report into this from the IO dispatcher while the UI collects on the
 * main thread, so every mutation goes through [MutableStateFlow.update]-style
 * compare-and-set rather than read-modify-write.
 *
 * @param clock injectable so tests can assert on elapsed time deterministically.
 */
class ConnectionProgressTracker(private val clock: () -> Long = System::currentTimeMillis) {
    private val _progress = MutableStateFlow<ConnectionProgress?>(null)
    val progress: StateFlow<ConnectionProgress?> = _progress.asStateFlow()

    /** Begin a new attempt, discarding any previous one. */
    fun begin() {
        _progress.value = ConnectionProgress(
            stage = ConnectionStage.entries.first(),
            stageStartedAtMillis = clock(),
        )
    }

    /**
     * Advance to [stage].
     *
     * Ignored unless [stage] is genuinely ahead of the current one — transports
     * report opportunistically and the auth loop can revisit an earlier phase, but
     * progress the user sees should only ever move forward. The detail belonged to
     * the stage being left, so it goes with it.
     */
    fun enter(stage: ConnectionStage) {
        _progress.update { current ->
            if (current == null || current.isFinished || stage.ordinal <= current.stage.ordinal) {
                current
            } else {
                current.copy(stage = stage, stageStartedAtMillis = clock(), detail = null)
            }
        }
    }

    /**
     * Replace the detail line shown under the running stage.
     *
     * Deliberately not sticky: the newest line wins, and [enter] drops it on the way
     * to the next stage. This is a live readout of what is happening right now, not
     * a record — the terminal's own connect log is the record.
     */
    fun detail(text: String?) {
        _progress.update { current ->
            if (current == null || current.isFinished) current else current.copy(detail = text)
        }
    }

    /** Suspend or resume stage timeouts around a prompt. */
    fun setWaitingOnUser(waiting: Boolean) {
        _progress.update { current ->
            if (current == null || current.isFinished) current else current.copy(waitingOnUser = waiting)
        }
    }

    fun succeed() = record(ConnectionOutcome.Succeeded)

    fun fail(stage: ConnectionStage, message: String?) = record(ConnectionOutcome.Failed(stage, message))

    /**
     * Settle the attempt on [outcome].
     *
     * Outcomes are sticky: the first to land is the one the user is shown. With one
     * exception — a failure carrying no message yields to one that can explain
     * itself, because the order these arrive in is not the order of usefulness.
     * Teardown frequently reports first and has nothing to say, while the transport
     * that actually knows the cause reports moments later; without this the card
     * would settle on a bare "Connection failed" whenever teardown won the race.
     */
    fun record(outcome: ConnectionOutcome) {
        _progress.update { current ->
            val existing = current?.outcome
            if (current == null || (existing != null && (existing.explainsItself() || !outcome.explainsItself()))) {
                current
            } else {
                current.copy(outcome = outcome, waitingOnUser = false)
            }
        }
    }
}

/** False only for a failure with no message — the one outcome that says nothing. */
private fun ConnectionOutcome.explainsItself(): Boolean = !(this is ConnectionOutcome.Failed && message.isNullOrBlank())
