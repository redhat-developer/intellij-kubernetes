/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.junit.Before
import org.junit.Test

class ClusterTest {

    private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
    private val client: NamespacedKubernetesClient = client(NAMESPACE2.metadata.name, allNamespaces)
    private val watchable1: WatchableResource = mock()
    private val watchable2: WatchableResource = mock()
    private val observable: ModelChangeObservable = mock()
    private lateinit var cluster: TestableCluster

    @Before
    fun before() {
        cluster = createCluster()
    }

    private fun createCluster(): TestableCluster {
        val cluster = spy<TestableCluster>(TestableCluster(observable))
        doReturn(
            listOf { watchable1 }, // returned on 1st call
            listOf { watchable2 }) // returned on 2nd call
            .whenever(cluster).getWatchableResources(any())
        return cluster
    }

    @Test
    fun `#getAllNamespaces should get namespaces from client`() {
        // given
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list()).items
    }

    @Test
    fun `2nd #getAllNamespaces should not get namespaces from client but used cached entries`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list()).items
    }

    @Test
    fun `#invalidate should call client#namespaces()#list()`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.invalidate()
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(2)).items
    }

    @Test
    fun `#getNamespace(name) should return namespace`() {
        // given
        cluster.getAllNamespaces()
        // when
        val namespace = cluster.getNamespace(NAMESPACE2.metadata.name)
        // then
        assertThat(namespace).isEqualTo(NAMESPACE2)
    }

    @Test
    fun `#getNamespace(name) should return null if inexistent name`() {
        // given
        cluster.getAllNamespaces()
        // when
        val namespace = cluster.getNamespace("bogus")
        // then
        assertThat(namespace).isNull()
    }

    @Test
    fun `#getNamespace(name) should not load namespace(s) from client but use cached ones`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.getNamespace(NAMESPACE2.metadata.name)
        // then
        verify(client.namespaces().list()).items
    }

    @Test
    fun `#getCurrentNamespace should query namespace from (configured) context`() {
        // given
        // when
        cluster.getCurrentNamespace()
        // then
        verify(client.configuration).namespace
    }

    @Test
    fun `#getCurrentNamespace should return 1st namespace in list of all namespaces if there's no (configured) context namespace`() {
        // given client has no current namespace
        whenever(client.configuration.namespace)
            .doReturn(null)
        // when
        val currentNamespace = cluster.getCurrentNamespace()
        // then returns 1st namespace in list of all namespaces
        assertThat(currentNamespace).isEqualTo(allNamespaces[0])
    }

    @Test
    fun `#setCurrentNamespace should remove watched resources for current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1)
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
        cluster.setCurrentNamespace(NAMESPACE1)
        // then
        val captor = argumentCaptor<List<WatchableResourceSupplier>>()
        verify(cluster.watch).addAll(captor.capture())
        val suppliers = captor.firstValue
        assertThat(suppliers.first().invoke()).isEqualTo(watchable2)
    }

    @Test
    fun `#setCurrentNamespace should invalidate (new) current namespace provider`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1)
        // then
        val provider = cluster.getNamespaceProvider(cluster.getCurrentNamespace()!!)
        verify(provider)!!.invalidate()
    }

    @Test
    fun `#setCurrentNamespace should fire change in current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1)
        // then
        verify(observable).fireCurrentNamespace(NAMESPACE1)
    }

    @Test
    fun `#setCurrentNamespace should set namespace in client`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1)
        // then
        verify(client.configuration).namespace = NAMESPACE1.metadata.name
    }

    @Test
    fun `#getNamespaceProvider(resource) should return provider for given resource`() {
        // given
        val pod = ClientMocks.resource<Pod>("pod in namespace2", NAMESPACE2.metadata.name)
        // when
        val provider = cluster.getNamespaceProvider(pod)
        // then
        assertThat(provider!!.namespace).isEqualTo(NAMESPACE2)
    }

    @Test
    fun `#getNamespaceProvider(namespace) should return provider for given namespace`() {
        // given
        // when
        val provider = cluster.getNamespaceProvider(NAMESPACE2)
        // then
        assertThat(provider!!.namespace).isEqualTo(NAMESPACE2)
    }

    @Test
    fun `#add(namespace) should add if namespace is not contained yet`() {
        // given
        // when
        val added = cluster.add(resource<Namespace>("namespace"))
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(namespace) should not add if already exists`() {
        // given
        // when
        val added = cluster.add(NAMESPACE2)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#add(namespace) should create new namespace provider`() {
        // given
        val name = "namespace"
        val namespace = resource<Namespace>(name)
        assertThat(cluster.getNamespaceProvider(name)).isNull()
        // when
        cluster.add(namespace)
        // then
        assertThat(cluster.getNamespaceProvider(name)).isNotNull
    }

    @Test
    fun `#add(namespace) should fire namespace added`() {
        // given
        // when
        val namespace = resource<Namespace>("namespace")
        cluster.add(namespace)
        // then
        verify(observable).fireAdded(namespace)
    }

    @Test
    fun `#add(pod) should add pod to namespace provider`() {
        // given
        val provider = cluster.getNamespaceProvider(NAMESPACE2.metadata.name)
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        // when
        cluster.add(pod)
        // then
        verify(provider)!!.add(pod)
    }

    @Test
    fun `#add(pod) should return true if pod was added to namespace provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        val provider = cluster.getNamespaceProvider(NAMESPACE2.metadata.name)
        doReturn(true).whenever(provider)!!.add(pod)
        // when
        val added = cluster.add(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#add(pod) should return false if pod is contained in unknown namespace`() {
        // given
        val pod = resource<Pod>("pod", "unknown namespace")
        assertThat(cluster.getNamespaceProvider(pod.metadata.namespace)).isNull()
        // when
        val added = cluster.add(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#remove(namespace) should remove if is contained`() {
        // given
        // when
        val removed = cluster.remove(NAMESPACE2)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#remove(namespace) should not remove if namespace is not contained yet`() {
        // given
        // when
        val removed = cluster.remove(resource<Namespace>("namespace"))
        // then
        assertThat(removed).isFalse()
    }

    @Test
    fun `#remove(namespace) should remove existing namespace provider`() {
        // given
        val name = NAMESPACE2.metadata.name
        val namespace = resource<Namespace>(name)
        assertThat(cluster.getNamespaceProvider(name)).isNotNull
        // when
        cluster.remove(namespace)
        // then
        assertThat(cluster.getNamespaceProvider(name)).isNull()
    }

    @Test
    fun `#remove(namespace) should fire namespace removed`() {
        // given
        // when
        cluster.remove(NAMESPACE2)
        // then
        verify(observable).fireRemoved(NAMESPACE2)
    }

    @Test
    fun `#remove(pod) should remove pod from namespace provider`() {
        // given
        val provider = cluster.getNamespaceProvider(NAMESPACE2.metadata.name)
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        // when
        cluster.remove(pod)
        // then
        verify(provider)!!.remove(pod)
    }

    @Test
    fun `#remove(pod) should return true if pod was removed from namespace provider`() {
        // given
        val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
        val provider = cluster.getNamespaceProvider(NAMESPACE2.metadata.name)
        doReturn(true).whenever(provider)!!.remove(pod)
        // when
        val removed = cluster.remove(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#remove(pod) should return false if pod is contained in unknown namespace`() {
        // given
        val pod = resource<Pod>("pod", "unknown namespace")
        assertThat(cluster.getNamespaceProvider(pod.metadata.namespace)).isNull()
        // when
        val removed = cluster.remove(pod)
        // then
        assertThat(removed).isFalse()
    }

    @Test
    fun `#close should close client`() {
        // given
        // when
        cluster.close()
        // then
        verify(client).close()
    }


    inner class TestableCluster(observable: ModelChangeObservable): Cluster(observable) {

        public override var watch = mock<KubernetesResourceWatch>()

        override fun createClient(): NamespacedKubernetesClient {
            // cannot be mocked, is called in constructor
            return this@ClusterTest.client
        }

        public override fun createNamespaceProvider(namespace: Namespace): NamespaceProvider {
            return mock {
                on { mock.namespace } doReturn namespace
            }
        }

        public override fun getWatchableResources(namespace: Namespace): List<WatchableResourceSupplier> {
            TODO("override with mocking")
        }
    }
}