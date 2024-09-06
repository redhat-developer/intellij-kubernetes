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
package com.redhat.devtools.intellij.kubernetes.model.client

import com.nhaarman.mockitokotlin2.*
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.kubeConfigFile
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import io.fabric8.kubernetes.api.model.AuthInfo
import io.fabric8.kubernetes.api.model.NamedAuthInfo
import io.fabric8.kubernetes.api.model.NamedAuthInfoBuilder
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.NamedContextBuilder
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class ClientConfigTest {

    private val ctx1 =
        namedContext("ctx1", "namespace1", "cluster1", "user1")
    private val ctx2 =
        namedContext("ctx2", "namespace2", "cluster2", "user2")
    private val ctx3 =
        namedContext("ctx3", "namespace3", "cluster3", "user3")
    private val ctx4 =
        namedContext("ctx4", "namespace4", "cluster4", "user4")
    private val ctx5 =
        namedContext("ctx5", "namespace5", "cluster5", "user5")
    private val ctx6 =
        namedContext("ctx6", "namespace6", "cluster6", "user6")
    private val currentContext = ctx2
    private val allContexts = listOf(ctx1, ctx2, ctx3)

    private val user5 = namedAuthInfo("user5", "user5", "token5")

    // kubeConfigFile current context ctx5
    private val ctx5FileWithCurrentContext = mockFile("ctx5FileWithCurrent")
    private val ctx5ConfigWithCurrentContext = kubeConfig(
        ctx5.name,
        null
    )
    private val ctx5KubeConfigFileWithCurrentContext = kubeConfigFile(
        ctx5FileWithCurrentContext,
        ctx5ConfigWithCurrentContext
    )

    // kubeConfigFile context ctx5
    private val ctx5FileWithCurrentNamespace = mockFile("ctx5FileWithContext")
    private val ctx5ConfigWithCurrentNamespace = kubeConfig(
        null,
        listOf(ctx4, ctx5, ctx6),
        listOf(user5)
    )
    private val ctx5KubeConfigFileWithCurrentNamespace = kubeConfigFile(
        ctx5FileWithCurrentNamespace,
        ctx5ConfigWithCurrentNamespace
    )
    private val config: Config = ClientMocks.config(
        currentContext,
        allContexts
    )
    private val client: Client = createClient(config)
    private val persistence: (io.fabric8.kubernetes.api.model.Config?, String?) -> Unit = mock()
    private val clientConfig = spy(TestableClientConfig(client, persistence))

    @Test
    fun `#currentContext should return config#currentContext`() {
        // given
        // when
        clientConfig.currentContext
        // then
        verify(config).currentContext
    }

    @Test
    fun `#allContexts should return config#allContexts`() {
        // given
        // when
        clientConfig.allContexts
        // then
        verify(config).contexts
    }

    @Test
    fun `#isCurrent should return true if context is equal`() {
        // given
        // when
        val isCurrent = clientConfig.isCurrent(currentContext)
        // then
        assertThat(isCurrent).isTrue()
    }

    @Test
    fun `#isCurrent should return false if context isn't equal`() {
        // given
        // when
        val isCurrent = clientConfig.isCurrent(ctx3)
        // then
        assertThat(isCurrent).isFalse()
    }

    @Test
    fun `#save should NOT save if no file with current context nor with current namespace exists`() {
        // given
        val config = client.configuration
        doReturn(null)
            .whenever(config).getFileWithCurrentContext()
        doReturn(null)
            .whenever(config).getFileWithContext(any())
        // when
        clientConfig.save().join()
        // then
        verify(persistence, never()).invoke(any(), any())

    }

    @Test
    fun `#save should NOT save if files are same in current namespace and current context than client config`() {
        // given
        val config = client.configuration
        // same current context
        whenever(config.currentContext)
            .thenReturn(ctx5)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(this.ctx5KubeConfigFileWithCurrentContext)
        // same current namespace
        whenever(config.getFileWithContext(ctx5.name))
            .thenReturn(ctx5KubeConfigFileWithCurrentNamespace)
        // when
        clientConfig.save().join()
        // then
        verify(persistence, never()).invoke(any(), any())
    }

    @Test
    fun `#save should save file with current context if it has different current context than client config`() {
        // given
        val config = client.configuration
        // different current context
        whenever(config.currentContext)
            .thenReturn(ctx6)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(ctx5KubeConfigFileWithCurrentContext)
        // same current namespace
        whenever(config.getFileWithContext(ctx5.name))
            .thenReturn(ctx5KubeConfigFileWithCurrentNamespace)
        // when
        clientConfig.save().join()
        // then
        verify(persistence).invoke(ctx5ConfigWithCurrentContext, ctx5FileWithCurrentContext.absolutePath)
    }

    @Test
    fun `#save should set current context in KubeConfigFile if it has different current context than client config`() {
        // given
        val config = client.configuration
        // different current context
        whenever(config.currentContext)
            .thenReturn(ctx6)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(ctx5KubeConfigFileWithCurrentContext)
        // same current namespace
        whenever(config.getFileWithContext(ctx5.name))
            .thenReturn(ctx5KubeConfigFileWithCurrentNamespace)
        // when
        clientConfig.save().join()
        // then
        verify(ctx5ConfigWithCurrentContext).currentContext = ctx6.name
    }

    @Test
    fun `#save should save file with current namespace if it has different current namespace than client config`() {
        // given
        val config = client.configuration
        // same current context
        whenever(config.currentContext)
            .thenReturn(ctx5)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(ctx5KubeConfigFileWithCurrentContext)
        // different current namespace
        val ctx5WithDifferentNamespace = kubeConfig(
            null,
            listOf(
                namedContext(ctx5.name,"R2-D2")
            )
        )
        val kubeConfigFile = kubeConfigFile(ctx5FileWithCurrentNamespace, ctx5WithDifferentNamespace)
        whenever(config.getFileWithContext(ctx5.name))
            .thenReturn(kubeConfigFile)
        // when
        clientConfig.save().join()
        // then
        verify(persistence).invoke(ctx5WithDifferentNamespace, ctx5FileWithCurrentNamespace.absolutePath)
    }

    @Test
    fun `#save should set current namespace if kubeConfigFile has different current namespace than client config`() {
        // given
        val config = client.configuration
        // same current context
        whenever(config.currentContext)
            .thenReturn(ctx5)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(ctx5KubeConfigFileWithCurrentContext)
        // different current namespace
        val ctx5ContextWithDifferentNamespace = namedContext(ctx5.name,"R2-D2")
        val ctx5ConfigWithDifferentNamespace = kubeConfig(
            null,
            listOf(
                ctx5ContextWithDifferentNamespace
            )
        )
        val kubeConfigFile = kubeConfigFile(ctx5FileWithCurrentNamespace, ctx5ConfigWithDifferentNamespace)
        whenever(config.getFileWithContext(ctx5.name))
            .thenReturn(kubeConfigFile)
        // when
        clientConfig.save().join()
        // then
        verify(ctx5ContextWithDifferentNamespace.context).namespace = ctx5.context.namespace
    }

    @Test
    fun `#save should save file with current context and file with current namespace if both differ from client config`() {
        // given
        val config = client.configuration
        // different current context
        whenever(config.currentContext)
            .thenReturn(ctx2)
        whenever(config.getFileWithCurrentContext())
            .thenReturn(ctx5KubeConfigFileWithCurrentContext)
        // different current namespace
        val ctx2KubeConfigFileWithCurrentNamespaceClone = kubeConfig(
            null,
            listOf(
                namedContext(ctx2.name,"R2-D2")
            )
        )
        val ctx2ConfigWithCurrentNamespaceClone = kubeConfigFile(ctx5FileWithCurrentNamespace, ctx2KubeConfigFileWithCurrentNamespaceClone)
        whenever(config.getFileWithContext(ctx2.name))
            .thenReturn(ctx2ConfigWithCurrentNamespaceClone)
        // when
        clientConfig.save().join()
        // then
        verify(persistence).invoke(ctx5ConfigWithCurrentContext, ctx5FileWithCurrentContext.absolutePath)
        verify(persistence).invoke(ctx2KubeConfigFileWithCurrentNamespaceClone, ctx5FileWithCurrentNamespace.absolutePath)
    }

    @Test
    fun `#isEqual should return true if given config is equal client config`() {
        // given
        val kubeConfig = kubeConfig(
            config.currentContext.name,
            config.contexts,
            listOf(namedAuthInfo(config.currentContext.context.user))
        )
        // when
        val isEqual = clientConfig.isEqual(kubeConfig)
        // then
        assertThat(isEqual).isTrue()
    }

    @Test
    fun `#isEqual should return false if given config differs from client config in current context`() {
        // given
        val kubeConfig = kubeConfig(
            "skywalker",
            config.contexts,
            listOf(namedAuthInfo(config.currentContext.context.user))
        )
        // when
        val isEqual = clientConfig.isEqual(kubeConfig)
        // then
        assertThat(isEqual).isFalse()
    }

    @Test
    fun `#isEqual should return false if given config differs from client config in current namespace`() {
        // given
        val differentNamespace = NamedContextBuilder(config.currentContext)
            .editContext()
                .withNamespace("skywalker")
            .endContext()
            .build()
        val allContexts = replaceByName(differentNamespace, config.contexts)
        val kubeConfig = kubeConfig(
            config.currentContext.name,
            allContexts // contains current context clone with different namespace
        )
        // when
        val isEqual = clientConfig.isEqual(kubeConfig)
        // then
        assertThat(isEqual).isFalse()
    }

    private fun replaceByName(context: NamedContext, allContexts: List<NamedContext>): List<NamedContext> {
        val newList = allContexts.toMutableList()
        newList.replaceAll {
            if (it.name == context.name) {
                context
            } else {
                it
            }
        }
        return newList
    }

    @Test
    fun `#isEqual should return false if given config differs from client config in token`() {
        // given
        whenever(config.autoOAuthToken)
            .doReturn("skywalker")
        val equalKubeConfig = kubeConfig(
            config.currentContext.name,
            allContexts,
            listOf(
                NamedAuthInfoBuilder()
                    .withName(config.currentContext.context.user)
                    .build()
            )
        )
        val differentToken = NamedAuthInfoBuilder()
            .withName(config.currentContext.context.user)
            .withNewUser()
                .withToken("iceplanet")
            .endUser()
            .build()
        val unequalKubeConfig = kubeConfig(
            config.currentContext.name,
            allContexts,
            listOf(differentToken)
        )
        // when
        val notEqual = clientConfig.isEqual(unequalKubeConfig)
        val equal = clientConfig.isEqual(equalKubeConfig)
        // then
        assertThat(equal).isTrue()
        assertThat(notEqual).isFalse()
    }

    private fun createClient(config: Config): Client {
        return mock {
            on { configuration } doReturn config
        }
    }

    private fun kubeConfig(
        currentContext: String?,
        allContexts: List<NamedContext>?,
        allUsers: List<NamedAuthInfo>? = null
    ): io.fabric8.kubernetes.api.model.Config {

        return mock {
            on { this.currentContext } doReturn currentContext
            on { this.contexts } doReturn allContexts
            on { this.users } doReturn allUsers
        }
    }

    private fun namedAuthInfo(name: String, username: String? = null, token: String? = null): NamedAuthInfo {
        val authInfo: AuthInfo = mock {
            on { this.username } doReturn username
            on { this.token } doReturn token
        }
        return mock {
            on { this.name } doReturn name
            on { this.user } doReturn authInfo
        }
    }

    private fun mockFile(absolutePath: String): File {
        return mock<File> {
            on { this.absolutePath } doReturn absolutePath
        }
    }

    private class TestableClientConfig(
        client: Client,
        persistence: (io.fabric8.kubernetes.api.model.Config?, absolutePath: String?) -> Unit
    ) : ClientConfig(client, { it.run() }, persistence)

}
