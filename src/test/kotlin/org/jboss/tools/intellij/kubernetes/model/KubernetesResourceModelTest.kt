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

import com.nhaarman.mockitokotlin2.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class KubernetesResourceModelTest {

    companion object Constants {
        val NAMESPACE1 = mockNamespace("namespace1")
        val NAMESPACE2 = mockNamespace("namespace2")
        val NAMESPACE3 = mockNamespace("namespace3")

        private fun mockNamespace(name: String): Namespace {
            val metadata = mock<ObjectMeta> {
                on { getName() } doReturn name
            }
            return mock {
                on { getMetadata() } doReturn metadata
            }
        }
    }

    private lateinit var client: NamespacedKubernetesClient
    private lateinit var model: IKubernetesResourceModel

    @Before
    fun before() {
        client = mockClient(listOf(
            NAMESPACE1,
            NAMESPACE2,
            NAMESPACE3))
        model = KubernetesResourceModel {
            val value = object : Cluster(it) {
                override fun createClient(): NamespacedKubernetesClient {
                    return this@KubernetesResourceModelTest.client
                }

                override fun getWatchableProviders(client: NamespacedKubernetesClient): List<() -> WatchableResource> {
                    return emptyList()
                }
            }
            value
        }
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

    @Test
    fun `should call list namespaces on client`() {
        // given
        // when
        model.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should not call list namespaces on client but used cached entries on 2nd call`() {
        // given
        model.getAllNamespaces()
        // when
        model.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should call list namespaces on client if refreshed before 2nd call`() {
        // given
        model.getAllNamespaces()
        // when
        model.clear()
        model.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(2)).items
    }

    @Test
    fun `should return namespace by name`() {
        // given
        model.getAllNamespaces()
        // when
        val namespace = model.getNamespace(NAMESPACE2.metadata.name)
        // then
        assertThat(namespace).isEqualTo(NAMESPACE2)
    }

    @Test
    fun `should return null if getting namespace by inexistent name`() {
        // given
        model.getAllNamespaces()
        // when
        val namespace = model.getNamespace("bogus")
        // then
        assertThat(namespace).isNull()
    }

    @Test
    fun `should not load namespace(s) from client but use cached ones when getting namespace by name`() {
        // given
        model.getAllNamespaces()
        // when
        val namespace = model.getNamespace(NAMESPACE2.metadata.name)
        // then
        verify(client.namespaces().list(), times(1)).items
    }

}
