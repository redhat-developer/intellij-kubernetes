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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test

class FailureCallbackOutputStreamTest {

    private val callback: (message: String) -> Unit = mock()
    private val consoleView: ConsoleView = mock()
    private val out = FailureCallbackOutputStream(callback, consoleView)

    @Test
    fun `should invoke callback has kind, status & message`() {
        // given
        val failure =
            "{" +
                "\"kind\":\"Status\"," +
                "\"apiVersion\":\"v1\"," +
                "\"metadata\":{}," +
                "\"status\":\"Failure\"," +
                "\"message\":\"container \"ngnix\" in pod \"nginx-initcontainers\" is waiting to start: trying and failing to pull image," +
                "\"reason\":\"BadRequest\"," +
                "\"code\":400" +
            "}\n"
        // when
        write(failure, out)
        // then
        verify(callback).invoke(any())
    }

    @Test
    fun `should NOT invoke callback if kind is NOT 'Status'`() {
        // given
        val failure =
            "{" +
                    " \"kind\":\"Station\"," +
                    " \"status\":\"Fail\"," +
                    " \"message\":\"lord vader has eaten too many chillies\"," +
                    "}"
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke(any())
    }

    @Test
    fun `should NOT invoke callback if there's no message`() {
        // given
        val failure =
            "{" +
                "\"kind\":\"Status\"," +
                "\"status\":\"Failure\"," +
            "}"
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke(any())
    }

    @Test
    fun `should NOT invoke callback if properties are NOT within json object`() {
        // given
        val failure =
            "{" +
            "}" +
            "\"kind\":\"Status\"," +
            "\"message\":\"luke skywalker loves chocolate\"" +
            "\"status\":\"Failure\"," +
            "}"
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke(any())
    }

    @Test
    fun `should NOT invoke callback if status is NOT 'Failure'`() {
        // given
        val failure =
            "{" +
                " \"kind\":\"Status\"," +
                " \"status\":\"Fail\"," +
                " \"message\":\"lord vader has eaten too many chillies\"," +
            "}"
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke(any())
    }

    private fun write(message: String, out: FailureCallbackOutputStream) {
        message
            .toCharArray()
            .map { it.toInt() }
            .forEach { out.write(it) }
    }
}