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

    private val callback: (message: String) -> Unit = mock()
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
        verify(callback).invoke(any())
    }

    @Test
    fun `should invoke callback, even if there's no message`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke(any())
    }

    @Test
    fun `should invoke callback, regardless of nested object`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "metadata":{},
            | "message":"the force is moot"
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke(any())
    }

    @Test
    fun `should invoke callback, regardless of non-json text in front of it`() {
        // given
        val failure = """
            |death star is not a star, it's an ugly spherical junk yard
            |we need to send the garbage collection to clean it up
            |{
            | "kind":"Status",
            | "status":"Failure",
            | "message":"yoda has the force"
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke(any())
    }

    @Test
    fun `should NOT invoke callback if properties are NOT within json object`() {
        // given
        val failure = """
            |{
            |}
            | "kind":"Status",
            | "message":"the force is weak"
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, never()).invoke(any())
    }

    @Test
    fun `should invoke callback if properties are in 2nd json object`() {
        // given
        val failure = """
            |{
            |}
            |{
            | "kind":"Status",
            | "message":"the force is moot"
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke(any())
    }

    @Test
    fun `should invoke callback 2x if there are 2 messages`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "message":"the force is moot"
            | "status":"Failure",
            |}
            |{
            | "kind":"Status",
            | "message":"the force is moot"
            | "status":"Failure",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback, times(2)).invoke(any())
    }

    @Test
    fun `should provide message to callback that it invokes`() {
        // given
        val failure = """
            |{
            | "kind":"Status",
            | "apiVersion":"v1",
            | "status":"Failure",
            | "message":"container \"ngnix\" in pod \"nginx-initcontainers\" is waiting to start: trying and failing to pull image",
            |}"
            """.trimMargin()
        // when
        write(failure, out)
        // then
        verify(callback).invoke("container \"ngnix\" in pod \"nginx-initcontainers\" is waiting to start: trying and failing to pull image")
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
        verify(callback, never()).invoke(any())
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
        verify(callback, never()).invoke(any())
    }

    private fun write(message: String, out: FailureCallbackOutputStream) {
        message
            .toCharArray()
            .map { it.toInt() }
            .forEach { out.write(it) }
    }
}