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
 * How a connection attempt ended.
 *
 * Sealed rather than an enum because the payloads differ, and that difference is the
 * whole point: "key exchange timed out after 30s" and "password rejected" are both
 * failures, but only the first tells the user their network is the problem.
 */
sealed interface ConnectionOutcome {
    /** The shell is live. */
    data object Succeeded : ConnectionOutcome

    /** The user abandoned the attempt. */
    data object Cancelled : ConnectionOutcome

    /** A stage exceeded its budget. [budgetMillis] is what it was allowed. */
    data class TimedOut(val stage: ConnectionStage, val budgetMillis: Long) : ConnectionOutcome

    /** The transport reported a failure. [message] is its own wording, if any. */
    data class Failed(val stage: ConnectionStage, val message: String?) : ConnectionOutcome
}
