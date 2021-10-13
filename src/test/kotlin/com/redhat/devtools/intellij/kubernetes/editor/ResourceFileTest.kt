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
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLFileType
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path

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
    fun `#create(virtualFile) should return null for file that has no kubernetes yaml`() {
        // given
        val filePath = ResourceFile.TEMP_FOLDER.resolve("darth-vader.doc")
        val virtualFile: VirtualFile = createVirtualFile(filePath,
            false,
            ByteArrayInputStream("bogus content".toByteArray(Charset.defaultCharset())))
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return true for yaml file on local filesystem with kube yaml`() {
        // given
        val factory = createResourceFileFactoryMock(YAMLFileType.YML)
        // when
        val isResourceFile = factory.isLocalYamlOrJson(temporaryVirtualFile)
        // then
        assertThat(isResourceFile).isTrue()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false if yaml file is not on local filesystem`() {
        // given
        val factory = createResourceFileFactoryMock(YAMLFileType.YML)
        val nonLocalFile: VirtualFile = createVirtualFile(
            path,
            false,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val isNotResourceFile = factory.isLocalYamlOrJson(nonLocalFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false if txt file is not on local filesystem`() {
        // given
        val factory = createResourceFileFactoryMock(PlainTextFileType.INSTANCE)
        val nonLocalFile: VirtualFile = createVirtualFile(
            path,
            false,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        // when
        val isNotResourceFile = factory.isLocalYamlOrJson(nonLocalFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false for yaml file that does not contain a kubernetes resource`() {
        // given
        val factory = createResourceFileFactoryMock(YAMLFileType.YML)
        val emptyFile: VirtualFile = createVirtualFile(
            path,
            true,
            ByteArrayInputStream("bogus content".toByteArray(Charset.defaultCharset())))
        // when
        val isNotResourceFile = factory.isLocalYamlOrJson(emptyFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false for null file`() {
        // given
        // when
        val isResourceFile = ResourceFile.isLocalYamlOrJson(null)
        // then
        assertThat(isResourceFile).isFalse()
    }

    @Test
    fun `#isTemporaryFile() should return true for file in tmp folder`() {
        // given
        // when
        val isTemporary = resourceFile.isTemporary()
        // then
        assertThat(isTemporary).isTrue()
    }

    @Test
    fun `#isTemporaryFile() should return false for file that's NOT in tmp folder`() {
        // given
        val virtualFileMock: VirtualFile = createVirtualFile(
            File(SystemProperties.getUserHome()).toPath().resolve("test.txt"),
            true,
            ByteArrayInputStream(job.toByteArray(Charset.defaultCharset())))
        val resourceFile = spy(TestableResourceFile(virtualFileMock))
        // when
        val isTemporary = resourceFile.isTemporary()
        // then
        assertThat(isTemporary).isFalse()
    }

    private fun createResourceFileFactoryMock(fileType: FileType): ResourceFile.Factory {
        val factory = spy(ResourceFile)
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
    fun `#deleteTemporary() should delete virtual file`() {
        // given
        // when
        resourceFile.deleteTemporary()
        // then
        verify(temporaryVirtualFile).delete(any())
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
        }
    }
}