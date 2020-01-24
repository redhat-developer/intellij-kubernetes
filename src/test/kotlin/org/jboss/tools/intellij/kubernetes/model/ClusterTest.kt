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

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
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
            .whenever(cluster).getWatchableResources()
        return cluster
    }

    @Test
    fun `#getAllNamespaces should get namespaces from client`() {
        // given
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `2nd #getAllNamespaces should not get namespaces from client but used cached entries`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
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
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `#getCurrentNamespace should query namespace from (configured) context`() {
        // given
        // when
        cluster.getCurrentNamespace()
        // then
        verify(client, times(1)).namespace
    }

    @Test
    fun `#getCurrentNamespace should return 1st namespace in list of all namespaces if there's no (configured) context namespace`() {
        // given client has no current namespace
        whenever(client.namespace)
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
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        val captor = argumentCaptor<List<WatchableResourceSupplier?>>()
        verify(cluster.watch, times(1)).removeAll(captor.capture())
        val suppliers = captor.firstValue
        assertThat(suppliers.first()?.invoke()).isEqualTo(watchable1)
    }

    @Test
    fun `#setCurrentNamespace should add new watched resources for new current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        val captor = argumentCaptor<List<WatchableResourceSupplier?>>()
        verify(cluster.watch, times(1)).addAll(captor.capture())
        val suppliers = captor.firstValue
        assertThat(suppliers.first()?.invoke()).isEqualTo(watchable2)
    }

    @Test
    fun `#setCurrentNamespace should invalidate (new) current namespace provider`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        val provider = cluster.getNamespaceProvider(cluster.getCurrentNamespace())
        verify(provider, times(1))!!.invalidate()
    }

    @Test
    fun `#setCurrentNamespace should fire change in current namespace`() {
        // given
        // when
        cluster.setCurrentNamespace(NAMESPACE1.metadata.name)
        // then
        verify(observable, times(1)).fireCurrentNamespace(NAMESPACE1)
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

        public override fun getWatchableResources(): List<WatchableResourceSupplier>? {
            TODO("override with mocking")
        }
    }
}