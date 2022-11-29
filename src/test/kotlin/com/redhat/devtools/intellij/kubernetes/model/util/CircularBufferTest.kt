/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CircularBufferTest {

    @Test
    fun `indexOf should return pos in buffer if string that is looked for is smaller than string in buffer`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Skywalker".length)
        buffer.offer("Luke Skywalker".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Sky".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(5)
    }

    @Test
    fun `indexOf should return pos in buffer if string that is looked for is identical to string in buffer`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Skywalker".length)
        buffer.offer("Luke Skywalker".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Luke Skywalker".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(0)
    }

    @Test
    fun `indexOf should return pos within buffer if wrapped buffer still has string that is looked for`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Skywalker".length - 1)
        buffer.offer("Luke Skywalker".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Sky".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(5)
    }

    @Test
    fun `indexOf should return -1 if wrapped buffer overwrote string that is looked for`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Skywalker".length - 1)
        buffer.offer("Luke Skywalker".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Luke".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(-1)
    }

    @Test
    fun `indexOf should return -1 if buffer does not contain string that is looked for`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Skywalker".length)
        buffer.offer("Luke Skywalker".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Darth Vader".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(-1)
    }

    @Test
    fun `indexOf should return -1 if buffer is smaller than string that is looked for`() {
        // given
        val buffer = CircularBuffer<Char>("Luke Sky".length)
        buffer.offer("Luke Sky".toCharArray().toTypedArray())
        // when
        val indexOf = buffer.indexOf("Luke Skywalker".toCharArray().toTypedArray())
        // then
        assertThat(indexOf).isEqualTo(-1)
    }
}