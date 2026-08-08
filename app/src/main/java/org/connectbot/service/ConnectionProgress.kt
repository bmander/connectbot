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

/**
 * An immutable snapshot of where a connection attempt has got to.
 *
 * Timestamps are stored rather than elapsed durations so that the flow emits only on
 * real transitions. Deriving "14s" is the overlay's job, on its own once-a-second
 * clock; if elapsed time lived here every session would re-emit every second whether
 * or not anyone was looking.
 *
 * @param stage the phase currently in progress.
 * @param stageStartedAtMillis when [stage] began.
 * @param detail context worth showing beside the stage label, such as a jump host
 *   nickname or the authentication method being tried.
 * @param waitingOnUser true while the attempt is parked on a prompt. Stage timeouts
 *   are suspended while this holds — someone typing a password must never be timed
 *   out.
 * @param outcome null while the attempt is live; set once, terminally.
 */
data class ConnectionProgress(
    val stage: ConnectionStage,
    val stageStartedAtMillis: Long,
    val detail: String? = null,
    val waitingOnUser: Boolean = false,
    val outcome: ConnectionOutcome? = null,
) {
    /** True once [outcome] is set, whether the attempt succeeded or not. */
    val isFinished: Boolean
        get() = outcome != null
}
