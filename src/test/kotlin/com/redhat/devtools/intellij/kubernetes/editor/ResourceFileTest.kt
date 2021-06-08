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

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.editor.AllowNonProjectEditing
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.startsWith
import java.nio.file.Path
import java.nio.file.Paths

class ResourceFileTest {

    private val path = ResourceFile.TEMP_FOLDER.resolve(
        "luke-skywalker.${ResourceFile.EXTENSION}")
    private val virtualFileMock: VirtualFile = mock {
        on { url } doReturn "file://$path"
    }
    private val resourceFile = spy(TestableResourceFile(path, null, virtualFileMock))

    @Test
    fun `#create(non-namespaced-HasMetadata) should create filename hasmetadata-name_yml in tmp`() {
        // given
        // when
        val file = ResourceFile.create(NAMESPACE1)
        // then
        assertThat(file?.path.toString()).startsWith(FileUtils.getTempDirectoryPath())
        assertThat(file?.path.toString()).endsWith("${NAMESPACE1.metadata.name}.${ResourceFile.EXTENSION}")
    }

    @Test
    fun `#create(namespaced-HasMetadata) should create filename namespace-name@hasmetadata-name_yml in tmp`() {
        // given
        // when
        val file = ResourceFile.create(POD1)
        // then
        assertThat(file?.path.toString()).startsWith(FileUtils.getTempDirectoryPath())
        assertThat(file?.path.toString()).endsWith("${POD1.metadata.name}@${POD1.metadata.namespace}.${ResourceFile.EXTENSION}")
    }

    @Test
    fun `#create(virtualFile) should return null ResourceFile for null virtualFile`() {
        // given
        val virtualFile = null
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#create(virtualFile) should use path of virtual file`() {
        // given
        val filePath = ResourceFile.TEMP_FOLDER.resolve("luke-skywalker.${ResourceFile.EXTENSION}")
        val virtualFile: VirtualFile = mock {
            on { url } doReturn "file://${filePath}"
        }
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file?.path).isEqualTo(filePath)
    }

    @Test
    fun `#create(virtualFile) should return null for file that has wrong extension`() {
        // given
        val filePath = ResourceFile.TEMP_FOLDER.resolve("darth-vader.doc")
        val virtualFile: VirtualFile = mock {
            on { url } doReturn "file://${filePath}"
        }
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#create(virtualFile) should return null for file that has wrong path`() {
        // given
        val filePath = "/var/darth-vader.${ResourceFile.EXTENSION}"
        val virtualFile: VirtualFile = mock {
            on { url } doReturn "file://${filePath}"
        }
        // when
        val file = ResourceFile.create(virtualFile)
        // then
        assertThat(file).isNull()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return true for file in temp folder with the correct extension`() {
        // given
        val resourceFile = ResourceFile.TEMP_FOLDER.resolve("r2d2.${ResourceFile.EXTENSION}")
        // when
        val isResourceFile = ResourceFile.isResourceFile(resourceFile)
        // then
        assertThat(isResourceFile).isTrue()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false for file not in temp`() {
        // given
        val nonResourceFile = Paths.get("/home", "princess-leia.${ResourceFile.EXTENSION}")
        // when
        val isNotResourceFile = ResourceFile.isResourceFile(nonResourceFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#isResourceFile(virtualFile) should return false for file with wrong extension`() {
        // given
        val nonResourceFile = ResourceFile.TEMP_FOLDER.resolve(
            "princess-leia.txt")
        // when
        val isNotResourceFile = ResourceFile.isResourceFile(nonResourceFile)
        // then
        assertThat(isNotResourceFile).isFalse()
    }

    @Test
    fun `#hasEqualBasePath(ResourceFile) should return true if other file has same path`() {
        // given
        val equalBasePath = TestableResourceFile(path, null, virtualFileMock)
        // when
        val samePath = resourceFile.hasEqualBasePath(equalBasePath)
        // then
        assertThat(samePath).isTrue()
    }

    @Test
    fun `#hasEqualBasePath(ResourceFile) should return true if other file has same path with addendum`() {
        // given
        val name = FileUtilRt.getNameWithoutExtension(path.toString())
        val suffix = FileUtilRt.getExtension(path.toString())
        val path = path.parent.resolve("$name(3).$suffix")
        val withAddendum = TestableResourceFile(path, null, virtualFileMock)
        // when
        val isEqualBasePath = resourceFile.hasEqualBasePath(withAddendum)
        // then
        assertThat(isEqualBasePath).isTrue()
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
        verify(virtualFileMock).refresh(any(), any())
    }

    @Test
    fun `#write(HasMetadata) should enable non-project file editing`() {
        // given
        // when
        resourceFile.write(POD3)
        // then
        verify(virtualFileMock).putUserData(AllowNonProjectEditing.ALLOW_NON_PROJECT_EDITING, true)
    }

    @Test
    fun `#delete() should delete virtual file`() {
        // given
        // when
        resourceFile.delete()
        // then
        verify(virtualFileMock).delete(any())
    }

    @Test
    fun `#rename() should rename virtual file to name of given resource`() {
        // given
        val expectedName = NAMESPACE2.metadata.name
        // when
        resourceFile.rename(NAMESPACE2)
        // then
        verify(virtualFileMock).rename(any(), eq("$expectedName.${ResourceFile.EXTENSION}"))
    }

    @Test
    fun `#rename() should add addendum if file already exists`() {
        // given file already exists
        val namespace2Path = path.parent.resolve(
            "${NAMESPACE2.metadata.name}.${ResourceFile.EXTENSION}")
        whenever(resourceFile.exists(namespace2Path))
            .doReturn(true)
        val newName = ArgumentCaptor.forClass(String::class.java)
        // when
        resourceFile.rename(NAMESPACE2)
        // then new name has addendum
        verify(virtualFileMock).rename(any(), newName.capture())
        assertThat(newName.value).isEqualTo("${NAMESPACE2.metadata.name}(1).${ResourceFile.EXTENSION}")
    }

    @Test
    fun `#rename() should increase addendum if file with addendum exists`() {
        // given file & file with addendum exists
        doAnswer { invocationOnMock ->
            val toCheck = invocationOnMock.arguments[0]
            path.parent.resolve("${NAMESPACE2.metadata.name}.${ResourceFile.EXTENSION}") == toCheck
                    || path.parent.resolve("${NAMESPACE2.metadata.name}(1).${ResourceFile.EXTENSION}") == toCheck
        }
        .whenever(resourceFile).exists(any())
        val newName = ArgumentCaptor.forClass(String::class.java)
        // when
        resourceFile.rename(NAMESPACE2)
        // then new name has addendum with increment
        verify(virtualFileMock).rename(any(), newName.capture())
        assertThat(newName.value).isEqualTo("${NAMESPACE2.metadata.name}(2).${ResourceFile.EXTENSION}")
    }

    @Test
    fun `#rename() should refresh virtual file`() {
        // given
        // when
        resourceFile.rename(NAMESPACE2)
        // then
        verify(virtualFileMock).refresh(any(), any())
    }

    private class TestableResourceFile(
        override val path: Path,
        public override var _virtualFile: VirtualFile?,
        private val virtualFileMock: VirtualFile
    ) : ResourceFile(path, _virtualFile) {

        public override fun exists(path: Path): Boolean {
            return super.exists(path)
        }

        public override fun write(content: String, path: Path) {
            super.write(content, path)
        }

        override fun findVirtualFile(path: Path): VirtualFile {
            return virtualFileMock
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
}