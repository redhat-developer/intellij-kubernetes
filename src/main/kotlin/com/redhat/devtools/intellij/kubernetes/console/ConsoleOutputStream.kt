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

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.logger
import java.io.OutputStream
import java.util.Queue
import java.util.concurrent.LinkedBlockingQueue

open class ConsoleOutputStream(private val terminal: ConsoleView) : OutputStream() {

    companion object {
        const val BUFFER_SIZE: Int = 256
    }

    private val buffer = LinkedBlockingQueue<Int>(BUFFER_SIZE)

    override fun write(char: Int) {
        if (!Character.isValidCodePoint(char)) {
            return
        } else if ('\n' == char.toChar()
            && buffer.remainingCapacity() > 0
        ) {
            // newline
            buffer.offer(char)
            flushToTerminal(buffer)
        } else if (!buffer.offer(char)) {
            // buffer full
            flushToTerminal(buffer)
            buffer.offer(char)
        } else {
            //
        }
    }

    private fun flushToTerminal(buffer: Queue<Int>) {
        val builder = buffer.stream()
            .collect(::StringBuilder, this::appendCodePoint, StringBuilder::append)
        buffer.clear()
        flushToTerminal(processOutput(builder.toString()))
    }

    protected open fun processOutput(output: String): String {
        return output
    }

    private fun flushToTerminal(output: String) {
        terminal.print(output, ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    private fun appendCodePoint(builder: java.lang.StringBuilder, codePoint: Int): java.lang.StringBuilder {
        try {
            builder.appendCodePoint(codePoint)
        } catch (e: Throwable) {
            logger<ConsoleOutputStream>().warn("Error appending code point $codePoint to buffer.", e)
        }
        return builder
    }
}