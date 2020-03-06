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
package org.jboss.tools.intellij.kubernetes.model.cluster

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.ModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.junit.Before
import org.junit.Test

class KubernetesClusterTest {

    private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
    private val currentNamespace = NAMESPACE2
    private val client: NamespacedKubernetesClient = client(currentNamespace.metadata.name, allNamespaces)
    private val watchable1: Watchable<Watch, Watcher<in HasMetadata>> = mock()
    private val watchable2: Watchable<Watch, Watcher<in HasMetadata>> = mock()
    private val observable: ModelChangeObservable = mock()
    private val namespacesProvider: INonNamespacedResourcesProvider<Namespace> = nonNamespacedResourceProvider(allNamespaces.toList())
    private val podsProvider: INamespacedResourcesProvider<Pod> = namespacedResourceProvider(emptyList(), currentNamespace)
    private lateinit var cluster: TestableKubernetesCluster

    @Before
    fun before() {
        cluster = createCluster()
    }

    private fun createCluster(): TestableKubernetesCluster {
        val resourceProviders: Map<Class<out HasMetadata>, IResourcesProvider<out HasMetadata>> =
            mutableMapOf(
                Pair(Namespace::class.java, namespacesProvider),
                Pair(Pod::class.java, podsProvider))
        val cluster = spy(TestableKubernetesCluster(observable, this@KubernetesClusterTest.client, resourceProviders))
        doReturn(
            listOf { watchable1 }, // returned on 1st call
            listOf { watchable2 }) // returned on 2nd call
            .whenever(cluster).getWatchableResources(any())
        return cluster
    }

    @Test
    fun `cluster instantiation should retrieve current namespace in client`() {
        // given
        // cluster created in #before
        // when
        // then
        verify(client.configuration).namespace
    }

    @Test
    fun `#setCurrentNamespace should remove watched resources for current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        val captor = argumentCaptor<List<WatchableResourceSupplier>>()
        verify(cluster.watch).removeAll(captor.capture())
        val suppliers = captor.firstValue
        assertThat(suppliers.first().invoke()).isEqualTo(watchable1)
    }

    @Test
    fun `#setCurrentNamespace should add new watched resources for new current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        val captor = argumentCaptor<List<WatchableResourceSupplier>>()
        verify(cluster.watch).addAll(captor.capture())
        val suppliers = captor.firstValue
        assertThat(suppliers.first().invoke()).isEqualTo(watchable2)
    }

    @Test
    fun `#setCurrentNamespace should set (new) namespace to all namespaced resource providers`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        // when
        cluster.setCurrentNamespace(namespace)
        // then
        verify(podsProvider).namespace = namespace
    }

    @Test
    fun `#setCurrentNamespace should not set (same) namespace to all namespaced resource providers if it didn't change`() {
        // given
        val namespace = currentNamespace.metadata.name
        // when
        cluster.setCurrentNamespace(namespace)
        // then
        verify(podsProvider, never()).namespace = namespace
    }

    @Test
    fun `#setCurrentNamespace should fire change in current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        verify(observable).fireCurrentNamespace(NAMESPACE1.metadata.name)
    }

    @Test
    fun `#setCurrentNamespace should set namespace in client`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        verify(client.configuration).namespace = NAMESPACE1.metadata.name
    }

    @Test
    fun `#getCurrentNamespace should retrieve current namespace in client`() {
        // given
        clearInvocations(client.configuration) // clear invocation when constructing cluster
        // when
        cluster.getCurrentNamespace()
        // then
        verify(client.configuration).namespace
    }

    @Test
    fun `#getCurrentNamespace should use 1st existing namespace if no namespace set in client`() {
        // given
            whenever(client.configuration.namespace)
                .thenReturn(null)
        // when
        val namespace = cluster.getCurrentNamespace()
        // then
        assertThat(namespace).isEqualTo(allNamespaces[0].metadata.name)
    }

    @Test
    fun `#getResources should get all resources in provider of given type`() {
        // given
        // when
        cluster.getResources(Namespace::class.java)
        // then
        verify(namespacesProvider).getAllResources()
    }

    @Test
    fun `#getResources should return empty list if there's no provider of given type`() {
        // given
        // when
        val services = cluster.getResources(Service::class.java)
        // then
        assertThat(services).isEmpty()
    }

    @Test
    fun `#add(namespace) should add namespace to namespaces provider`() {
        // given
        val namespace = resource<Namespace>("papa smurf namespace")
        // when
        cluster.add(namespace)
        // then
        verify(namespacesProvider).add(namespace)
        verify(podsProvider, never()).add(namespace)
    }

    @Test
    fun `#add(pod) should add pod to pods provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        // when
        cluster.add(pod)
        // then
        verify(podsProvider).add(pod)
    }

    @Test
    fun `#add(pod) should return true if pod was added to pods provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        doReturn(true)
            .whenever(podsProvider)!!.add(pod)
        // when
        val added = cluster.add(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(pod) should return false if pod was not added to pods provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        doReturn(false)
            .whenever(podsProvider).add(pod)
        // when
        val added = cluster.add(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#add(pod) should fire if provider added pod`() {
        // given
        val pod = resource<Pod>("gargamel")
        doReturn(true)
            .whenever(podsProvider).add(pod)
        // when
        cluster.add(pod)
        // then
        verify(observable).fireAdded(pod)
    }

    @Test
    fun `#remove(pod) should remove pod from pods provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        // when
        cluster.remove(pod)
        // then
        verify(podsProvider).remove(pod)
    }

    @Test
    fun `#invalidate() should invalidate all resource providers`() {
        // given
        // when
        cluster.invalidate()
        // then
        verify(namespacesProvider).invalidate()
        verify(podsProvider).invalidate()
    }

    @Test
    fun `#invalidate(resource) should invalidate resource provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        // when
        cluster.invalidate(pod)
        // then
        verify(namespacesProvider, never()).invalidate(any())
        verify(podsProvider).invalidate(pod)
    }

    @Test
    fun `#close should close client`() {
        // given
        // when
        cluster.close()
        // then
        verify(client).close()
    }

    inner class TestableKubernetesCluster(
        observable: ModelChangeObservable,
        client: NamespacedKubernetesClient,
        override val resourceProviders: Map<Class<out HasMetadata>, IResourcesProvider<out HasMetadata>>
    ) : KubernetesCluster(observable, client) {

        public override var watch = mock<ResourceWatch>()

        public override fun getWatchableResources(namespace: String): List<WatchableResourceSupplier?> {
            TODO("override with mocking")
        }

    }
}