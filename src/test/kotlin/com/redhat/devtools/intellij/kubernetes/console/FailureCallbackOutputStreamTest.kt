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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import java.io.OutputStream
import org.junit.Test

class FailureCallbackOutputStreamTest {

    private val callback: () -> Unit = mock()
    private val parent: OutputStream = mock()
    private val out = FailureCallbackOutputStream(callback, parent)

    @Test
    fun `should invoke callback if detects kind & status`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "apiVersion":"v1",
            | "metadata":{},
            | "status":"Failure",
            | "message":"container \"ngnix\" in pod \"nginx-initcontainers\" is waiting to start: trying and failing to pull image",
            | "reason":"BadRequest",
            | "code":400
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke()
    }

    @Test
    fun `should invoke callback if detects, regardless of nested object`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "metadata":{},
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke()
    }

    @Test
    fun `should invoke callback if detects, regardless of non-json text in front of it`() {
        // given
        val failure = """
            |death star is not a star, it's an ugly spherical junk yard
            |we need to send the garbage collection to clean it up
            |{
            | "kind":"Status",
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke()
    }

    @Test
    fun `should NOT invoke callback if properties are NOT within json object`() {
        // given
        val failure = """
            |{
            |}
            | "kind":"Status",
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke()
    }

    @Test
    fun `should invoke callback if properties are in 2nd json object`() {
        // given
        val failure = """
            |{
            |}
            |{
            | "kind":"Status",
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke()
    }

    @Test
    fun `should invoke callback 2x if there are 2 messages`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "status":"Failure",
            |}
            |{
            | "kind":"Status",
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, times(2)).invoke()
    }

    @Test
    fun `should write to parent outputstream even if does not detect`() {
        // given
        val failure = "{}"
        // when
        write(failure, out)
        // then
        verify(parent, times(failure.length)).write(any<Int>())
    }

    @Test
    fun `should NOT invoke callback if does not detect status`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "status":"<<JEDI>>",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke()
    }

    @Test
    fun `should NOT invoke callback if status is 'Fail' NOT 'Failure'`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "status":"Fail",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke()
    }

    private fun write(message: String, out: FailureCallbackOutputStream) {
        message
            .toCharArray()
            .map { it.toInt() }
            .forEach { out.write(it) }
    }
}