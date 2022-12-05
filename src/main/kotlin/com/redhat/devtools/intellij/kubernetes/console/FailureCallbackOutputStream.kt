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

class FailureCallbackOutputStream(private val onFailure: (message: String) -> Unit, out: OutputStream): FilterOutputStream(out) {

    companion object {
        enum class State {
            DETECT_FAILURE_OBJECT_START,
            DETECT_KIND_STATUS,
            DETECT_STATUS_FAILURE,
            DETECT_MESSAGE_PROPERTY,
            DETECT_MESSAGE_VALUE_START,
            DETECT_MESSAGE_VALUE_STOP
        }

        private const val OBJECT_START = '{'.toInt()
        private const val OBJECT_STOP = '}'.toInt()
        private val KIND_STATUS = toIntArray("\"kind\":\"Status\"")
        private val STATUS_FAILURE = toIntArray("\"status\":\"Failure\"")
        private val MESSAGE_PROPERTY = toIntArray("\"message\"")
        private const val QUOTATION_MARK = '"'.toInt()
        private const val ESCAPE = '\\'.toInt()

        private fun toIntArray(string: String): Array<Int> {
            return string.toByteArray(Charset.defaultCharset())
                .map { it.toInt() }
                .toTypedArray()
        }
    }

    private val buffer = CircularBuffer<Int>(512)
    private var state: State = State.DETECT_FAILURE_OBJECT_START
    private var nestingLevel = 0
    private var escapedChar = false
    private val message: StringBuilder = java.lang.StringBuilder()

    override fun write(b: Int) {
        out.write(b)
        detect(b)
    }

    private fun detect(b: Int) {
        when (state) {
            State.DETECT_FAILURE_OBJECT_START -> detectFailureObjectStart(b)
            State.DETECT_KIND_STATUS -> detectKindStatus(b)
            State.DETECT_STATUS_FAILURE -> detectStatusFailure(b)
            State.DETECT_MESSAGE_PROPERTY -> detectMessageProperty(b)
            State.DETECT_MESSAGE_VALUE_START -> detectMessageValueStart(b)
            State.DETECT_MESSAGE_VALUE_STOP -> detectMessageValueStop(b)
        }
    }

    private fun detectFailureObjectStart(b: Int): Boolean {
        return if (b == OBJECT_START) {
            if (++nestingLevel == 1) {
                state = State.DETECT_KIND_STATUS // only search 'kind: status' in level 0
            }
            true
        } else {
            false
        }
    }

    private fun detectFailureObjectStop(b: Int): Boolean {
        return if (b == OBJECT_STOP) {
            if (--nestingLevel <= 0) {
                nestingLevel = 0 // be resilient with wrong nesting
                state = State.DETECT_FAILURE_OBJECT_START  // only start new search when back on level 0
            }
            true
        } else {
            false
        }
    }

    private fun detectKindStatus(b: Int) {
        buffer.offer(b)
        if (detectFailureObjectStart(b)) {
            return
        } else if (detectFailureObjectStop(b)) {
            return
        } else if (buffer.contains(KIND_STATUS)) {
            state = State.DETECT_STATUS_FAILURE
        }
    }

    private fun detectStatusFailure(b: Int) {
        buffer.offer(b)
        if (detectFailureObjectStart(b)) {
            return
        } else if (detectFailureObjectStop(b)) {
            return
        } else if (buffer.contains(STATUS_FAILURE)) {
            // detected successfully, start detecting next
            state = State.DETECT_MESSAGE_PROPERTY
        }
    }

    private fun detectMessageProperty(b: Int) {
        buffer.offer(b)
        if (detectFailureObjectStart(b)) {
            return
        } else if (detectFailureObjectStop(b)) {
            // json object ends without message
            invokeFailureCallback()
            return
        } else if (buffer.contains(MESSAGE_PROPERTY)) {
            state = State.DETECT_MESSAGE_VALUE_START
        }
    }

    private fun detectMessageValueStart(b: Int) {
        buffer.offer(b)
        if (detectFailureObjectStart(b)) {
            // nested object
            return
        } else if (detectFailureObjectStop(b)) {
            // json object ends without message
            invokeFailureCallback()
            return
        } else if (b == QUOTATION_MARK){
            // value start detected
            state = State.DETECT_MESSAGE_VALUE_STOP
        }
    }

    private fun detectMessageValueStop(b: Int) {
        buffer.offer(b)
        if (detectFailureObjectStart(b)) {
            return
        } else if (detectFailureObjectStop(b)) {
            // json object ends without message
            invokeFailureCallback()
            return
        } else if (b == ESCAPE) {
            // ignore escape character & take next character literally
            escapedChar = true
        } else if (escapedChar) {
            // escaped character, take it literally
            escapedChar = false
            message.append(b.toChar())
        } else if (b == QUOTATION_MARK) {
            // value stop detected
            invokeFailureCallback()
        } else {
            message.append(b.toChar())
        }
    }

    private fun invokeFailureCallback() {
        state = State.DETECT_FAILURE_OBJECT_START
        nestingLevel = 0
        onFailure.invoke(message.toString())
    }

}