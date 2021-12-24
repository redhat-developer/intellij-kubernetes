/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.editor.PersistentEditorValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PersistentEditorValueTest {

    private val value = "joe dalton"
    private val virtualFile: VirtualFile = mock()
    private val editor: FileEditor = mock {
        on { file } doReturn virtualFile
    }
    private val fileAttribute: FileAttribute = mock {
        on { readAttributeBytes(any()) } doReturn value.toByteArray()
    }

    val editorValue = PersistentEditorValue(editor, fileAttribute)

    @Test
    fun `#get should read from persistence`() {
        // given
        // when
        editorValue.get()
        // then
        verify(fileAttribute).readAttributeBytes(virtualFile)
    }

    @Test
    fun `#get should only read from persistence 1x when called 3x`() {
        // given
        // when
        editorValue.get()
        editorValue.get()
        editorValue.get()
        // then
        verify(fileAttribute, times(1)).readAttributeBytes(virtualFile)
    }

    @Test
    fun `#get should only NOT read from persistence if value is already set`() {
        // given
        editorValue.set("yoda")
        // when
        editorValue.get()
        // then
        verify(fileAttribute, never()).readAttributeBytes(virtualFile)
    }

    @Test
    fun `#set should override value read from file attribute`() {
        // given
        assertThat(editorValue.get()).isEqualTo(value)
        val overriding = "new value"
        // when
        editorValue.set(overriding)
        // then
        val retrieved = editorValue.get()
        assertThat(retrieved).isEqualTo(overriding)
    }

    @Test
    fun `#save should save value to persistence`() {
        // given
        // when
        editorValue.save()
        // then
        verify(fileAttribute).writeAttributeBytes(virtualFile, value.toByteArray())
    }

    @Test
    fun `#save should save empty array to persistence if value is null`() {
        // given
        editorValue.set(null)
        // when
        editorValue.save()
        // then
        verify(fileAttribute).writeAttributeBytes(virtualFile, ByteArray(0))
    }
}