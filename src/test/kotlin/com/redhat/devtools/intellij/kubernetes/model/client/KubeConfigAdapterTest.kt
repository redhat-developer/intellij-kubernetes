/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.client

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import io.fabric8.kubernetes.api.model.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import java.io.File

class KubeConfigAdapterTest {

    private val ctx1 = namedContext("Aldeeran", mock())
    private val ctx2 = namedContext("Dantooine", mock())
    private val ctx3 = namedContext("Tatooine", mock())

    private val currentContext = ctx2.name

    private val file: File = mock()
    private val config: Config = mock {
        on { currentContext } doReturn currentContext
        on { contexts } doReturn listOf(ctx1, ctx2, ctx3)
    }
    private val loadKubeConfig: (file: File) -> Config = mock { on { invoke(file) } doReturn config }
    private val saveKubeConfig: (config: Config, file: File) -> Unit = mock()
    private val adapter = KubeConfigAdapter(file, config, mock(), saveKubeConfig)

    @Test
    fun `#save should persist given config to given file`() {
        // given
        // when
        adapter.save()
        // then
        verify(saveKubeConfig).invoke(config, file)
    }

    @Test
    fun `#save should load config if it wasn't specified`() {
        // given
        val file: File = mock {
            on { exists() } doReturn true
        }
        val adapter = KubeConfigAdapter(file, null, loadKubeConfig, saveKubeConfig)
        // when
        adapter.save()
        // then
        verify(loadKubeConfig).invoke(file)
    }

    @Test
    fun `#save should NOT save config if the file doesn't exist`() {
        // given
        val file: File = mock {
            on { exists() } doReturn false // doesn't exist
        }
        val adapter = KubeConfigAdapter(file, null, loadKubeConfig, saveKubeConfig)
        // when
        adapter.save()
        // then
        verify(saveKubeConfig, never()).invoke(config, file)
    }

    @Test
    fun `#setCurrentContext should set current context if it is different than the on in the file`() {
        // given
        val file: File = mock {
            on { exists() } doReturn true
        }
        val adapter = KubeConfigAdapter(file, config, loadKubeConfig, saveKubeConfig)
        // when
        adapter.setCurrentContext("obiwan")
        // then
        verify(config).currentContext = "obiwan"
    }

    @Test
    fun `#setCurrentContext should NOT set current context if it is the same as the one in the file`() {
        // given
        val adapter = KubeConfigAdapter(file, config, loadKubeConfig, saveKubeConfig)
        // when
        val modified = adapter.setCurrentContext(config.currentContext) // same current-context
        // then
        verify(config, never()).currentContext = anyString()
        assertThat(modified).isFalse()
    }

    @Test
    fun `#setCurrentNamespace should set current namespace if it is different than the on in the file`() {
        // given
        val adapter = KubeConfigAdapter(file, config, loadKubeConfig, saveKubeConfig)
        // when
        adapter.setCurrentNamespace(ctx2.name, "obiwan")
        // then
        verify(ctx2.context).namespace = "obiwan"
    }

    @Test
    fun `#setCurrentNamespace should NOT set current namespace if it is the same as the on in the file`() {
        // given
        val adapter = KubeConfigAdapter(file, config, loadKubeConfig, saveKubeConfig)
        // when
        val modified = adapter.setCurrentNamespace(ctx2.name, ctx2.context.namespace) // same namespace
        // then
        verify(ctx2.context, never()).namespace = anyString()
        assertThat(modified).isFalse()
    }
}