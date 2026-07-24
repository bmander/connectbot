/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeKeyGestureTest {
    private val threshold = 100f
    private val leftKeys = "\u0002P" // Ctrl+B P
    private val rightKeys = "\u0002N" // Ctrl+B N

    @Test
    fun `swipe left beyond threshold returns left keys`() {
        assertEquals(leftKeys, resolveSwipeKeys(-150f, threshold, leftKeys, rightKeys))
    }

    @Test
    fun `swipe right beyond threshold returns right keys`() {
        assertEquals(rightKeys, resolveSwipeKeys(150f, threshold, leftKeys, rightKeys))
    }

    @Test
    fun `swipe left below threshold returns null`() {
        assertNull(resolveSwipeKeys(-50f, threshold, leftKeys, rightKeys))
    }

    @Test
    fun `swipe right below threshold returns null`() {
        assertNull(resolveSwipeKeys(50f, threshold, leftKeys, rightKeys))
    }

    @Test
    fun `swipe exactly at threshold returns null`() {
        assertNull(resolveSwipeKeys(-100f, threshold, leftKeys, rightKeys))
        assertNull(resolveSwipeKeys(100f, threshold, leftKeys, rightKeys))
    }

    @Test
    fun `swipe left with empty left keys returns null`() {
        assertNull(resolveSwipeKeys(-150f, threshold, "", rightKeys))
    }

    @Test
    fun `swipe right with empty right keys returns null`() {
        assertNull(resolveSwipeKeys(150f, threshold, leftKeys, ""))
    }

    @Test
    fun `both keys empty always returns null`() {
        assertNull(resolveSwipeKeys(-150f, threshold, "", ""))
        assertNull(resolveSwipeKeys(150f, threshold, "", ""))
    }

    @Test
    fun `zero drag returns null`() {
        assertNull(resolveSwipeKeys(0f, threshold, leftKeys, rightKeys))
    }
}
