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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.NAMESPACE3
import org.junit.Before
import org.junit.Test

class ClusterTest {

    private lateinit var cluster: Cluster
    private lateinit var client: NamespacedKubernetesClient

    @Before
    fun before() {
        client = mockClient(listOf(
            NAMESPACE1,
            NAMESPACE2,
            NAMESPACE3))
        cluster = object : Cluster(mock()) {
                override fun createClient(): NamespacedKubernetesClient {
                    return this@ClusterTest.client
                }

                override fun getWatchableProviders(client: NamespacedKubernetesClient): List<() -> WatchableResource> {
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
    fun `should call list namespaces on client if cleared after 1st & before 2nd call`() {
        // given
        cluster.getAllNamespaces()
        // when
        cluster.clear()
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
        val namespace = cluster.getNamespace(NAMESPACE2.metadata.name)
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    private fun mockClient(namespaces: List<Namespace>): NamespacedKubernetesClient {
        val namespaceList = mock<NamespaceList> {
            on { items } doReturn namespaces
        }
        val namespacesMock =
            mock<NamespaceListOperation> {
                on { list() } doReturn namespaceList
            }
        return mock<NamespacedKubernetesClient> {
            on { namespaces() } doReturn namespacesMock
        }
    }
}
