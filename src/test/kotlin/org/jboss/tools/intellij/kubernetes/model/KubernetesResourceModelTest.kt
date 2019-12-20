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
import io.fabric8.kubernetes.api.model.DoneableNamespace
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.junit.Before
import org.junit.Test
typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class KubernetesResourceModelTest {

    private lateinit var client: NamespacedKubernetesClient
    private lateinit var model: IKubernetesResourceModel

    @Before
    fun before() {
        client = mockClient(listOf(mockNamespace("namespace1"), mockNamespace("namespace2")))
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

    private fun mockNamespace(name: String): Namespace {
        val metadata = mock<ObjectMeta> {
            on { getName() } doReturn name
        }
        return mock {
            on { getMetadata() } doReturn metadata
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
        // when
        model.getAllNamespaces()
        model.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(1)).items
    }

    @Test
    fun `should call list namespaces on client if refreshed`() {
        // given
        // when
        model.getAllNamespaces()
        model.refresh()
        model.getAllNamespaces()
        // then
        verify(client.namespaces().list(), times(2)).items
    }

/*    private val client = mockk<NamespacedKubernetesClient>() {
        every { namespaces() } returns
                mockk () {
                    every { list() } returns
                            mockk () {
                                every { items } returns
                                        emptyList()
                            }
                    every { watch(any()) } returns
                            mockk()
                }
    }

    private lateinit var model: KubernetesResourceModel

    @Before
    fun before() {
        model = KubernetesResourceModelImpl(
            fun (resourceChange: ResourceChangeObservable): KubernetesCluster {
                val cluster = KubernetesCluster(resourceChange)
                val spy = spyk(cluster, recordPrivateCalls = true)
                every { spy invokeNoArgs "createClient" } throws RuntimeException() //returns client
                return spy
            })
    }

    @After
    fun after() {
    }

    @Test
    fun `should call list namespaces on client`() {
        // given
        //mockNamespaces(emptyList())
        // when
        model.getAllNamespaces()
        // then
        verify { client.namespaces().list().items }
    }

    private fun mockNamespaces(namespaces: List<Namespace>) {
        // client.namespaces().list().items.asSequence()
        every { anyConstructed<DefaultKubernetesClient>().namespaces() } returns
                mockk (relaxed = true) {
                        every { list() } returns
                                mockk (relaxed = true) {
                                    every { items } returns
                                            namespaces
                                }
                        every { watch(any()) } returns
                                mockk(relaxed = true)
                }
    }

    private fun mockNamespace(name: String): Namespace {
        return mockk {
            every { metadata } returns
                    mockk() {
                        every { getName() } returns
                                name
                    }
        }
    }
*/
}
