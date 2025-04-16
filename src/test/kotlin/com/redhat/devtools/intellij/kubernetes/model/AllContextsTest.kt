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
package com.redhat.devtools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.activeContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.clientAdapter
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.clientConfig
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.clientFactory
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.context
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.Mockito

class AllContextsTest {

    private val modelChange: IResourceModelObservable = mock()
    private val namedContext1 =
        namedContext("ctx1", NAMESPACE1.metadata.name, "cluster1", "user1")
    private val namedContext2 =
        namedContext("ctx2", NAMESPACE2.metadata.name, "cluster2", "user2")
    private val namedContext3 =
        namedContext("ctx3", NAMESPACE3.metadata.name, "cluster3", "user3")
    private val currentContext = namedContext2
    private val namespace: Namespace = resource(namedContext2.context.namespace, null, "someNamespaceUid", "v1")
    private val activeContext: IActiveContext<HasMetadata, KubernetesClient> = activeContext(namespace, currentContext)
    private val contextFactory: (ClientAdapter<out KubernetesClient>, IResourceModelObservable) -> IActiveContext<out HasMetadata, out KubernetesClient> =
        Mocks.contextFactory(activeContext)
    private val contexts = listOf(namedContext1, currentContext, namedContext3)
    private val token = "42"
    private val client = client(true)
    private val clientConfig = clientConfig(currentContext, contexts)
    private val clientAdapter = clientAdapter<ClientAdapter<KubernetesClient>>(clientConfig, client)
    private val clientFactory = clientFactory(clientAdapter)

    private val allContexts = TestableAllContexts(modelChange, contextFactory, clientFactory)

    @Test
    fun `when instantiated, it should watch kube config`() {
        // given
        // when
        // then
        assertThat(allContexts.watchStarted).isTrue
    }

    @Test
    fun `#refresh() should close existing context`() {
        // given
        // when
        allContexts.refresh()
        // then
        verify(activeContext).close()
    }

    @Test
    fun `#refresh() causes contexts be reloaded from client config`() {
        // given
        val newNamedContexts = listOf(namedContext3, namedContext2, namedContext1)
        doReturn(newNamedContexts)
            .whenever(clientConfig).allContexts
        // when
        allContexts.refresh()
        // then
        assertThat(allContexts.all)
            .extracting(IContext::name, IContext::namespace)
            .containsExactly(
                tuple(newNamedContexts[0].name, newNamedContexts[0].context.namespace),
                tuple(newNamedContexts[1].name, newNamedContexts[1].context.namespace),
                tuple(newNamedContexts[2].name, newNamedContexts[2].context.namespace)
            )
    }

    @Test
    fun `#refresh() causes current context to be reloaded from client config`() {
        // given
        val newCurrent = namedContext1
        doReturn(activeContext(resource(newCurrent.context.namespace, apiVersion = "v1"), newCurrent))
            .whenever(contextFactory).invoke(any(), any())
        // when
        allContexts.refresh()
        // then
        assertThat(allContexts.current)
            .matches { context -> context?.name == newCurrent.name }
            .matches { context -> context?.namespace == newCurrent.context.namespace }
    }

    @Test
    fun `#refresh() should fire all contexts modified`() {
        // given
        // when
        allContexts.refresh()
        // then
        verify(modelChange).fireAllContextsChanged()
    }

    @Test
    fun `#all should not load twice`() {
        // given
        // when
        allContexts.all
        allContexts.all
        // then
        verify(clientConfig, times(1)).allContexts
    }

    @Test
    fun `#current should NOT create new context if there's an active context in list of all contexts`() {
        // given
        doReturn(listOf(namedContext1, namedContext2, namedContext3))
            .whenever(clientConfig).allContexts
        doReturn(namedContext1)
            .whenever(clientConfig).currentContext
        allContexts.all // create list of all contexts
        clearInvocations(contextFactory)
        // when
        allContexts.current
        // then
        verify(contextFactory, never()).invoke(any(), any())
    }

    @Test
    fun `#current() should create new context if #refresh() was called before`() {
        // given
        allContexts.current // trigger creation of context
        allContexts.refresh()
        clearInvocations(contextFactory)
        // when
        allContexts.current
        // then
        verify(contextFactory).invoke(any(), any()) // anyOrNull() bcs NamedContext is nullable
    }

    @Test
    fun `#setCurrentContext(context) should NOT create new active context if same context is already set`() {
        // given
        allContexts.current // create current context
        clearInvocations(contextFactory) // clear invocation so that it's not counted
        // when
        allContexts.setCurrentContext(activeContext)
        // then
        verify(contextFactory, never()).invoke(any(), any())
    }

    @Test
    fun `#setCurrentContext(context) should create new active context`() {
        // given
        assertThat(allContexts.current?.name).isNotEqualTo(namedContext3.name) // create current context
        clearInvocations(contextFactory) // clear invocation so that it's not counted
        // when
        allContexts.setCurrentContext(context(namedContext3))
        // then
        allContexts.all // reload contexts
        verify(contextFactory).invoke(any(), any())
    }

    @Test
    fun `#setCurrentContext(context) should replace existing context in list of all contexts`() {
        // given
        val allContexts = TestableAllContexts(modelChange, contextFactory, clientFactory)
        val newCurrentContext = context(namedContext1)
        assertThat(allContexts.current).isNotEqualTo(newCurrentContext)
        val old = allContexts.current
        assertThat(old).isNotEqualTo(newCurrentContext)
        allContexts.all // create all contexts
        val activeContext = activeContext(resource(newCurrentContext.namespace!!), namedContext1)
        /**
         * Trying to use {@code com.nhaarman.mockitokotlin2.doReturn} leads to
         * "Overload Resolution Ambiguity" with {@code org.mockito.Mockito.doReturn} in intellij.
         * Gradle compiles it just fine
         *
         * @see <a href="https://youtrack.jetbrains.com/issue/KT-22961">KT-22961</a>
         * @see <a href="https://stackoverflow.com/questions/38779666/how-to-fix-overload-resolution-ambiguity-in-kotlin-no-lambda">fix-overload-resolution-ambiguity</a>
         */
        Mockito.doReturn(activeContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        val currentContext = allContexts.setCurrentContext(newCurrentContext)
        // then
        assertThat(allContexts.all).contains(currentContext)
        assertThat(allContexts.all).doesNotContain(old)
    }


    @Test
    fun `#setCurrentContext(context) should close current context`() {
        // given
        allContexts.all // create all contexts
        val newCurrentContext = activeContext(namespace, namedContext3)
        val currentContext = allContexts.current!!
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        allContexts.setCurrentContext(newCurrentContext)
        // then
        verify(currentContext).close()
    }

    @Test
    fun `#setCurrentContext(context) should create new client`() {
        // given
        allContexts.all // create all contexts
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        clearInvocations(clientFactory)
        // when
        allContexts.setCurrentContext(newCurrentContext)
        // then
        verify(clientFactory).invoke(anyOrNull(), anyOrNull())
    }

    @Test
    fun `#setCurrentContext(context) should fireAllContextsChanged`() {
        // given
        allContexts.all // create all contexts
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        allContexts.setCurrentContext(newCurrentContext)
        // then
        verify(modelChange).fireAllContextsChanged()
    }

    @Test
    fun `#setCurrentContext(context) should NOT watch existing kinds on new context`() {
        // given
        allContexts.all // create all contexts
        Mockito.doReturn(
            listOf(
                mock<ResourceKind<Pod>>(),
                mock<ResourceKind<Deployment>>()
            )
        )
            .`when`(activeContext).getWatched()
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        allContexts.setCurrentContext(newCurrentContext)
        // then
        verify(newCurrentContext, never()).watch(any<ResourceKind<*>>())
    }

    @Test
    fun `#setCurrentContext(context) should save client config `() {
        // given
        allContexts.all // create all contexts
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        allContexts.setCurrentContext(newCurrentContext)
        // then
        verify(clientConfig).save()
    }

    @Test
    fun `#setCurrentContext(context) should cause all contexts to be reloaded from client`() {
        // given
        allContexts.all // create all contexts
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        clearInvocations(clientConfig)
        // when
        allContexts.setCurrentContext(newCurrentContext)
        allContexts.all // cause reload
        // then
        verify(clientConfig).allContexts
    }

    @Test
    fun `#setCurrentNamespace(namespace) should watch existing kinds on new context`() {
        // given
        allContexts.all // create all contexts
        val podKind = mock<ResourceKind<Pod>>()
        val deploymentKind = mock<ResourceKind<Deployment>>()
        Mockito.doReturn(listOf(podKind, deploymentKind))
            .`when`(activeContext).getWatched()
        val newCurrentContext = activeContext(namespace, namedContext3)
        Mockito.doReturn(newCurrentContext)
            .`when`(contextFactory).invoke(any(), any()) // returned on 2nd call
        // when
        allContexts.setCurrentNamespace("darth-vader")
        // then
        verify(newCurrentContext).watchAll(argThat(ArgumentMatcher {
            it.contains(podKind)
                    && it.contains(deploymentKind)
                    && it.size == 2
        }))
    }

    @Test
    fun `#setCurrentNamespace(namespace) should return null if current context is null`() {
        // given
        val clientConfig = clientConfig(null, contexts)
        val client = client(true)
        val clientAdapter = clientAdapter<ClientAdapter<KubernetesClient>>(clientConfig, client)
        val clientFactory = clientFactory(clientAdapter)
        val allContexts = TestableAllContexts(modelChange, contextFactory, clientFactory)
        // when
        val newContext = allContexts.setCurrentNamespace("rebellion")
        // then
        assertThat(newContext).isNull()
    }

    @Test
    fun `#setCurrentNamespace(namespace) should observable#fireCurrentNamespaceChanged`() {
        // given
        // when
        allContexts.setCurrentNamespace("dark side")
        // then
        verify(modelChange).fireCurrentNamespaceChanged(anyOrNull(), anyOrNull())
    }

    @Test
    fun `#setCurrentNamespace(namespace) should NOT observable#fireCurrentNamespaceChanged if new current context is null`() {
        // given
        val client = client(true)
        val clientAdapter = clientAdapter<ClientAdapter<KubernetesClient>>(null, client) // no config so there are no contexts
        val clientFactory = clientFactory(clientAdapter)
        val allContexts = TestableAllContexts(modelChange, contextFactory, clientFactory)
        // when
        allContexts.setCurrentNamespace("dark side")
        // then
        verify(modelChange, never()).fireCurrentNamespaceChanged(anyOrNull(), anyOrNull())
    }

    @Test
    fun `#setCurrentNamespace(namespace) should create client with given namespace`() {
        // given
        // when
        allContexts.setCurrentNamespace("dark side")
        // then
        val namespace = ArgumentCaptor.forClass(String::class.java)
        val contextName = ArgumentCaptor.forClass(String::class.java)
        // 2x: init, setCurrentContext
        verify(clientFactory, times(2)).invoke(namespace.capture(), contextName.capture())
        assertThat(namespace.allValues[1]).isEqualTo("dark side")
        assertThat(contextName.allValues[1]).isEqualTo(activeContext.name)
    }

    @Test
    fun `#onKubeConfigChanged() should fire if updated config is null but error is NOT null`() {
        // given
        // when
        allContexts.onKubeConfigChanged(null, mock())
        // then
        verify(modelChange).fireAllContextsChanged()
    }

    @Test
    fun `#onKubeConfigChanged() should fire if updated config is null and error is null`() {
        // given
        // when
        allContexts.onKubeConfigChanged(null, null)
        // then
        verify(modelChange).fireAllContextsChanged()
    }

    @Test
    fun `#onKubeConfigChanged() should NOT fire if existing config and updated config are equal`() {
        // given
        doReturn(true)
            .whenever(clientConfig).isEqualConfig(any())
        // when
        allContexts.onKubeConfigChanged(mock(), null)
        // then
        verify(modelChange, never()).fireAllContextsChanged()
    }

    @Test
    fun `#onKubeConfigChanged() should fire if existing config and updated config are not equal`() {
        // given
        doReturn(false)
            .whenever(clientConfig).isEqualConfig(any())
        // when
        allContexts.onKubeConfigChanged(mock(), null)
        // then
        verify(modelChange).fireAllContextsChanged()
    }

    @Test
    fun `#onKubeConfigChanged() should close client if existing config and updated config are not equal`() {
        // given
        doReturn(false)
            .whenever(clientConfig).isEqualConfig(any())
        // when
        allContexts.onKubeConfigChanged(mock(), null)
        // then
        verify(clientAdapter).close()
    }

    @Test
    fun `#onKubeConfigChanged() should close current context if existing config and updated config are not equal`() {
        // given
        doReturn(false)
            .whenever(clientConfig).isEqualConfig(any())
        // when
        allContexts.onKubeConfigChanged(mock(), mock())
        // then
        verify(activeContext).close()
    }

    @Test
    fun `#onKubeConfigChanged() should get all contexts (again) if existing config and updated config are not equal`() {
        // given
        doReturn(false)
            .whenever(clientConfig).isEqualConfig(any())
        // when
        allContexts.onKubeConfigChanged(mock(), mock())
        // then
        verify(clientConfig).allContexts
    }

    /**
     * Returns a client mock that answers with the given boolean to the call
     * [client.namespaces().withName("<name>").isReady]
     *
     * @param namespaceResourceIsReady the boolean to return to the call
     * @return a client mock that answers to the query if a given namespace is ready
     */
    private fun client(namespaceResourceIsReady: Boolean): KubernetesClient {
        val namespaceResource: Resource<Namespace> = mock {
            on { isReady } doReturn namespaceResourceIsReady
        }
        val namespacesOperation: NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>> = mock {
            on { withName(any()) } doReturn namespaceResource
        }
        return client(namespacesOperation)
    }

    private fun client(namespacesOp: NonNamespaceOperation<Namespace, NamespaceList, Resource<Namespace>>): KubernetesClient {
        return mock<KubernetesClient> {
            on { namespaces() } doReturn namespacesOp
        }
    }

    private fun client(e: KubernetesClientException): KubernetesClient {
        return mock<KubernetesClient> {
            on { namespaces() } doThrow e
        }
    }

    private class TestableAllContexts(
        modelChange: IResourceModelObservable,
        contextFactory: (ClientAdapter<out KubernetesClient>, IResourceModelObservable) -> IActiveContext<out HasMetadata, out KubernetesClient>,
        clientFactory: (String?, String?) -> ClientAdapter<out KubernetesClient>
    ) : AllContexts(contextFactory, modelChange, clientFactory) {

        var watchStarted = false

        public override fun onKubeConfigChanged(updated: Config?, error: Exception?) {
            super.onKubeConfigChanged(updated, error)
        }

        override fun reportTelemetry(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
            // prevent telemetry reporting
        }

        override fun runAsync(runnable: () -> Unit) {
            runnable.invoke() // run directly, not in IDEA pooled threads
        }

        override fun watchKubeConfigs() {
            // don't watch filesystem (override super method)
            watchStarted = true
        }
    }
}