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
package com.redhat.devtools.intellij.kubernetes.console

import com.redhat.devtools.intellij.kubernetes.model.util.CircularBuffer
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

class FailureCallbackOutputStream(private val onFailure: () -> Unit, out: OutputStream): FilterOutputStream(out) {

    companion object {
        enum class State {
            DETECT_JSON,
            DETECT_KIND_STATUS,
            DETECT_STATUS_FAILURE
        }

        private const val JSON_START = '{'.toInt()
        private const val JSON_STOP = '}'.toInt()
        private val KIND_STATUS = toIntArray("\"kind\":\"Status\"")
        private val STATUS_FAILURE = toIntArray("\"status\":\"Failure\"")

        private fun toIntArray(string: String): Array<Int> {
            return string.toByteArray(Charset.defaultCharset())
                .map { it.toInt() }
                .toTypedArray()
        }
    }

    private val buffer = CircularBuffer<Int>(512)
    private var state: State = State.DETECT_JSON
    private var nestingLevel = 0

    override fun write(b: Int) {
        out.write(b)
        detect(b)
    }

    private fun detect(b: Int) {
        when (state) {
            State.DETECT_JSON -> detectObjectStart(b)
            State.DETECT_KIND_STATUS -> detectKindStatus(b)
            State.DETECT_STATUS_FAILURE -> detectStatusFailure(b)
        }
    }

    private fun detectObjectStart(b: Int): Boolean {
        return if (b == JSON_START) {
            if (++nestingLevel == 1) {
                state = State.DETECT_KIND_STATUS // only search 'kind: status' in level 0
            }
            true
        } else {
            false
        }
    }

    private fun detectObjectStop(b: Int): Boolean {
        return if (b == JSON_STOP) {
            if (--nestingLevel <= 0) {
                nestingLevel = 0 // be resilient with wrong nesting
                state = State.DETECT_JSON  // only start new search when back on level 0
            }
            true
        } else {
            false
        }
    }

    private fun detectKindStatus(b: Int) {
        buffer.offer(b)
        if (detectObjectStart(b)) {
            return
        } else if (detectObjectStop(b)) {
            return
        } else if (buffer.contains(KIND_STATUS)) {
            state = State.DETECT_STATUS_FAILURE
        }
    }

    private fun detectStatusFailure(b: Int) {
        buffer.offer(b)
        if (detectObjectStart(b)) {
            return
        } else if (detectObjectStop(b)) {
            return
        } else if (buffer.contains(STATUS_FAILURE)) {
            // detected successfully, start detecting next
            state = State.DETECT_JSON
            nestingLevel = 0
            onFailure.invoke()
        }
    }

}