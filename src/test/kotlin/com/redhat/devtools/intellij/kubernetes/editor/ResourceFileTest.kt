/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLFileType
import org.junit.Test

class ResourceFileTest {

    private val job: String = """
apiVersion: batch/v1
kind: Job
metadata:
  name: countdown
spec:
  template:
    metadata:
      name: countdown
    spec:
      containers:
      - name: counter
        image: centos:7
        command:
         - "bin/bash"
         - "-c"
         - "echo kube"
      restartPolicy: Never
"""
    private val path = ResourceFile.TEMP_FOLDER.resolve(
        "luke-skywalker.${ResourceFile.EXTENSION}")
    private val temporaryVirtualFile: VirtualFile = createVirtualFile(
        path,
        true,
        ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
    private val resourceFile = spy(TestableResourceFile(temporaryVirtualFile))

    private val nonTemporaryVirtualFile =  createVirtualFile(
        File(SystemProperties.getUserHome()).toPath().resolve("test.txt"),
        true,
        ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))


    @Test
    fun `#create(virtualFile) should return null for null virtualFile`() {
        // given
        val virtualFile = null
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#create(virtualFile) should return null for file that is not in local filesystem`() {
        // given
        val filePath = ResourceFile.TEMP_FOLDER.resolve("darth-vader.doc")
        val virtualFile: VirtualFile = createVirtualFile(filePath,
            false,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#create(virtualFile) should return null for file that is no yaml or json file`() {
        // given
        val factory = createResourceFileFactoryMock(HtmlFileType.INSTANCE)
        val filePath = ResourceFile.TEMP_FOLDER.resolve("darth-vader.html")
        val virtualFile: VirtualFile = createVirtualFile(filePath,
            true,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val file = factory.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#isValidType(virtualFile) should return false if yaml file is not on local filesystem`() {
        // given
        val factory = createResourceFileFactoryMock(YAMLFileType.YML)
        val nonLocalFile: VirtualFile = createVirtualFile(
            path,
            false,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val isNotResourceFile = factory.isValidType(nonLocalFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isValidType(virtualFile) should return false if txt file is not on local filesystem`() {
        // given
        val factory = createResourceFileFactoryMock(PlainTextFileType.INSTANCE)
        val nonLocalFile: VirtualFile = createVirtualFile(
            path,
            false,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val isNotResourceFile = factory.isValidType(nonLocalFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isValidType(virtualFile) should return false for null file`() {
        // given
        // when
        val isResourceFile = ResourceFile.isValidType(null)
        // then
        assertThat(isResourceFile).isFalse()
    }

    @Test
    fun `#isTemporary(virtualFile) should return false for null file`() {
        // given
        // when
        val isResourceFile = ResourceFile.isTemporary(null)
        // then
        assertThat(isResourceFile).isFalse()
    }

    @Test
    fun `#isTemporary(virtualFile) should return true for file in temp dir`() {
        // given
        // when
        val isResourceFile = ResourceFile.isTemporary(temporaryVirtualFile)
        // then
        assertThat(isResourceFile).isTrue()
    }

    @Test
    fun `#isTemporary(virtualFile) should return false for file that's NOT in tmp folder`() {
        // given
        // when
        val isResourceFile = ResourceFile.isTemporary(nonTemporaryVirtualFile)
        // then
        assertThat(isResourceFile).isFalse()
    }

    @Test
    fun `#isTemporary() should return true for file in tmp folder`() {
        // given
        // when
        val isTemporary = resourceFile.isTemporary()
        // then
        assertThat(isTemporary).isTrue()
    }

    @Test
    fun `#isTemporary() should return false for file that's NOT in tmp folder`() {
        // given
        val resourceFile = spy(TestableResourceFile(nonTemporaryVirtualFile))
        // when
        val isTemporary = resourceFile.isTemporary()
        // then
        assertThat(isTemporary).isFalse()
    }

    private fun createResourceFileFactoryMock(fileType: FileType): ResourceFile.Factory {
        val factory = spy(ResourceFile.Factory)
        doReturn(fileType)
            .whenever(factory).getFileType(any())
        return factory
    }

    @Test
    fun `#write(HasMetadata) should write path`() {
        // given
        // when
        resourceFile.write(POD3)
        // then
        verify(resourceFile).write(any(), eq(path))
    }

    @Test
    fun `#write(HasMetadata) should refresh virtual file`() {
        // given
        // when
        resourceFile.write(POD3)
        // then
        verify(temporaryVirtualFile).refresh(any(), any())
    }

    @Test
    fun `#write(HasMetadata) should enable non-project file editing`() {
        // given
        // when
        resourceFile.write(POD3)
        // then
        verify(temporaryVirtualFile).putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }

    @Test
    fun `#deleteTemporary() should delete temporary virtual file if it's valid`() {
        // given
        // when
        resourceFile.deleteTemporary()
        // then
        verify(temporaryVirtualFile).delete(any())
    }

    @Test
    fun `#deleteTemporary() should NOT delete temporary virtual file if it's NOT valid`() {
        // given
        doReturn(false)
            .whenever(temporaryVirtualFile).isValid
        // when
        resourceFile.deleteTemporary()
        // then
        verify(temporaryVirtualFile, never()).delete(any())
    }

    @Test
    fun `#deleteTemporary() should NOT delete temporary virtual file if it doesn't exist`() {
        // given
        doReturn(false)
            .whenever(temporaryVirtualFile).exists()
        // when
        resourceFile.deleteTemporary()
        // then
        verify(temporaryVirtualFile, never()).delete(any())
    }

    @Test
    fun `#deleteTemporary() should NOT delete virtual file if it's not temporary`() {
        // given
        val path = File(SystemProperties.getUserHome()).toPath().resolve("non-temporary")
        val nonTemporary: VirtualFile = createVirtualFile(
            path,
            true,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        val resourceFile = spy(TestableResourceFile(nonTemporary))
        // when
        resourceFile.deleteTemporary()
        // then
        verify(nonTemporary, never()).delete(any())
    }

    private class TestableResourceFile(
        override var virtualFile: VirtualFile
    ) : ResourceFile(virtualFile) {

        public override fun exists(path: Path): Boolean {
            return super.exists(path)
        }

        public override fun write(content: String, path: Path) {
            super.write(content, path)
        }

        override fun <R> executeReadAction(callable: () -> R): R {
            // dont use Application thread pool
            return callable.invoke()
        }

        override fun executeWriteAction(runnable: () -> Unit) {
            // dont use Application thread pool
            runnable.invoke()
        }
    }

    private fun createVirtualFile(filePath: Path, inLocalFileSystem: Boolean, inputStream: InputStream): VirtualFile {
        return mock {
            on { url } doReturn "file://$filePath"
            on { path } doReturn filePath.toString()
            on { isInLocalFileSystem() } doReturn inLocalFileSystem
            on { getInputStream() } doReturn inputStream
            on { isValid() } doReturn true
            on { exists() } doReturn true
        }
    }
}