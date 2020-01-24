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

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.junit.Before
import org.junit.Test

class ClusterTest {

    private lateinit var cluster: Cluster
    private lateinit var client: NamespacedKubernetesClient
    private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)

    @Before
    fun before() {
        client = client(NAMESPACE2.metadata.name, allNamespaces)
        cluster = object : Cluster(mock()) {
                override fun createClient(): NamespacedKubernetesClient {
                    return this@ClusterTest.client
                }

                override fun getWatchableResources(): List<WatchableResourceSupplier>? {
                    return emptyList()
                }
            }
        }

    @Test
    fun `should call list namespaces on client`() {
        // given
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should not call list namespaces on client but used cached entries on 2nd call`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should call client#namespaces()#list() if cluster is invalidated`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.invalidate()
        cluster.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(2)).items
    }

    @Test
    fun `should return namespace by name`() {
        // given
        cluster.getAllNamespaces()
        // when
        val namespace = cluster.getNamespace(NAMESPACE2.metadata.name)
        // then
        assertThat(namespace).isEqualTo(NAMESPACE2)
    }

    @Test
    fun `should return null if getting namespace by inexistent name`() {
        // given
        cluster.getAllNamespaces()
        // when
        val namespace = cluster.getNamespace("bogus")
        // then
        assertThat(namespace).isNull()
    }

    @Test
    fun `should not load namespace(s) from client but use cached ones when getting namespace by name`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.getNamespace(NAMESPACE2.metadata.name)
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should query namespace from (configured) context when #getCurrentNamespace`() {
        // given
        // when
        cluster.getCurrentNamespace()
        // then
        verify(client, times(1)).namespace
    }

    @Test
    fun `should return 1st namespace in list of all namespaces if there's no (configured) context namespace`() {
        // given client has no current namespace
        doReturn(null)
            .whenever(client).namespace
        // when
        val currentNamespace = cluster.getCurrentNamespace()
        // then returns 1st namespace in list of all namespaces
        assertThat(currentNamespace).isEqualTo(allNamespaces[0])
    }

}
