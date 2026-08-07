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
import java.util.concurrent.atomic.AtomicLong

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

    private val _attemptId = AtomicLong(0)

    /**
     * Identifies the current attempt.
     *
     * A transport captures this when it starts and re-checks it before reporting
     * success, so that a handshake which completes after the user cancelled cannot
     * resurrect a bridge that has already been torn down.
     */
    val attemptId: Long
        get() = _attemptId.get()

    /** Begin a new attempt, discarding any previous one. Returns the new [attemptId]. */
    fun begin(): Long {
        val now = clock()
        _progress.value = ConnectionProgress(
            stage = ConnectionStage.entries.first(),
            startedAtMillis = now,
            stageStartedAtMillis = now,
        )
        return _attemptId.incrementAndGet()
    }

    /**
     * Advance to [stage].
     *
     * Ignored if the attempt has already finished, or if [stage] is not ahead of the
     * current one — transports report opportunistically and a retry loop can revisit
     * an earlier phase, but progress the user sees should only ever move forward.
     */
    fun enter(stage: ConnectionStage, detail: String? = null) {
        _progress.update { current ->
            if (current == null || current.isFinished) return@update current
            when {
                // Already past this stage: ignore entirely.
                stage.ordinal < current.stage.ordinal -> current

                // Re-reporting the stage we are already on. Leave the clock alone,
                // and leave any detail alone unless a new one was supplied — a bare
                // re-report must not wipe the detail line the connect log just set.
                stage == current.stage ->
                    if (detail == null) current else current.copy(detail = detail)

                // Moving on. The detail belonged to the stage we are leaving, so it
                // goes with it.
                else -> current.copy(
                    stage = stage,
                    stageStartedAtMillis = clock(),
                    detail = detail,
                )
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

    fun succeed() = finish(ConnectionOutcome.Succeeded)

    fun cancel() = finish(ConnectionOutcome.Cancelled)

    fun timeOut(stage: ConnectionStage, budgetMillis: Long) = finish(ConnectionOutcome.TimedOut(stage, budgetMillis))

    fun fail(stage: ConnectionStage, message: String?) = finish(ConnectionOutcome.Failed(stage, message))

    /** Drop all progress state, hiding any overlay. */
    fun clear() {
        _progress.value = null
    }

    /** Outcomes are sticky: the first one to land is the one the user is shown. */
    private fun finish(outcome: ConnectionOutcome) {
        _progress.update { current ->
            if (current == null || current.isFinished) {
                current
            } else {
                current.copy(outcome = outcome, waitingOnUser = false)
            }
        }
    }
}
