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
 * The phases a connection attempt passes through, in the order they occur.
 *
 * Declaration order is meaningful: a stage is "done" precisely when its ordinal is
 * below the current stage's, which is what lets the progress overlay derive its
 * done/current/pending rendering without tracking a separate completed set.
 *
 * [HANDSHAKING] is deliberately coarse. sshlib performs the TCP connect, version
 * banner exchange and key exchange inside a single opaque call, so there is no
 * honest way to subdivide it. The overlay compensates by showing elapsed time,
 * which is what actually tells a user whether an attempt is stuck.
 */
enum class ConnectionStage {
    /** Resolving the hostname to one or more addresses. */
    RESOLVING,

    /** TCP connect, protocol version exchange and key exchange. */
    HANDSHAKING,

    /** Checking the server's host key against the known-hosts store. */
    VERIFYING_HOST_KEY,

    /** Offering credentials: public key, keyboard-interactive or password. */
    AUTHENTICATING,

    /** Opening the channel, requesting a PTY and starting the shell. */
    OPENING_SESSION,
}
