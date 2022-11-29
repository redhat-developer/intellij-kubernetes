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

class CircularBuffer<E : Any>(val capacity: Int) {
    private val buffer: Array<E?> = arrayOfNulls<Any>(capacity) as Array<E?>

    @Volatile
    private var writePos: Int = -1

    @Volatile
    private var readPos: Int = 0

    val isEmpty: Boolean
        get() = writePos <= readPos

    fun offer(elements: Array<E>) {
        elements.forEach { offer(it) }
    }

    fun offer(element: E) {
        val bufferOffset = ++writePos % capacity
        buffer[bufferOffset] = element
    }

    fun poll(): E? {
        return if (!isEmpty) {
            buffer[++readPos % capacity]
        } else {
            null
        }
    }

    fun size(): Int {
        return writePos - readPos + 1
    }

    fun clear() {
        readPos = 0
        writePos = 0
    }

    fun contains(toMatch: Array<E>): Boolean {
        return indexOf(toMatch) >= 0
    }

    fun indexOf(toMatch: Array<E>): Int {
        var occurrenceStart = readPos % capacity
        var occurrenceOffset = 0
        while ((occurrenceStart + occurrenceOffset) < writePos
            && occurrenceOffset < toMatch.size) {
            val checked = buffer[occurrenceStart + occurrenceOffset]
            if (checked == toMatch[occurrenceOffset]) {
                occurrenceOffset++
            } else {
                occurrenceStart++
                occurrenceOffset = 0
            }
        }
        return if (occurrenceOffset < toMatch.size - 1) {
            // not found
            -1
        } else {
            occurrenceStart
        }
    }
}