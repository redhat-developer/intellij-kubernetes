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

import com.intellij.terminal.TerminalExecutionConsole
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test

class ConsoleOutputStreamTest {

    private val POOR_DARTH_VADER =
        "Numerous Disney World customers were annoyed after one of the actors playing Darth Vader actually had trouble breathing." +
                "The actor has now been sent to a galaxy far, far away, meaning that he has been fired and will not be receiving employee health benefits." +
                "Hopefully in that galaxy they have at least free sabers."
    private var terminal: TerminalExecutionConsole = mock()
    private var out: ConsoleOutputStream = ConsoleOutputStream(terminal)

    @Before
    fun before() {
        this.terminal = mock()
        this.out = spy(ConsoleOutputStream(terminal))
    }

    @Test
    fun `#write should print to terminal as soon as character LF`() {
        // given
        val toPrint = "luke skywalker\n"
        // when
        printToTerminal(toPrint)
        // then print all chars
        verify(terminal, times(1)).print(eq(toPrint), any())
    }

    @Test
    fun `#write should NOT print to terminal as long as buffer is not full`() {
        // given
        val toPrint = POOR_DARTH_VADER.substring(0, ConsoleOutputStream.BUFFER_SIZE - 1)
        // when
        printToTerminal(toPrint)
        // then
        verify(terminal, never()).print(any(), any())
    }

    @Test
    fun `#write should print to terminal as soon as buffer is full`() {
        // given
        val toPrint = POOR_DARTH_VADER.substring(0, ConsoleOutputStream.BUFFER_SIZE + 1)
        // when
        printToTerminal(toPrint)
        // then print chars but last one
        verify(terminal, times(1)).print(eq(toPrint.substring(0, toPrint.length - 1)), any())
    }

    @Test
    fun `#write should skip invalid code point`() {
        // given
        val toPrint = "" + (Character.MAX_CODE_POINT + 1) + "\n"
        // when
        printToTerminal(toPrint)
        // then print chars but invalid code point
        verify(terminal, never()).print(eq("\n"), any())
    }

    private fun printToTerminal(string: String) {
        string.forEach { out.write(it.toInt()) }
    }
}