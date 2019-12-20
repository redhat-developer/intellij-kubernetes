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

import io.fabric8.kubernetes.api.model.DoneableNamespace
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceList
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

typealias NamespaceListOperation = NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>

class KubernetesResourceModelTest {

    private lateinit var client: NamespacedKubernetesClient
    private lateinit var model: KubernetesResourceModel

    @Before
    fun before() {
        val namespaceList = mockk<NamespaceList>(relaxed = true) {
            every { items } returns emptyList()
        }
        val nonNamespaceOperation: NamespaceListOperation = mockk(relaxed = true) {
            every { list() } returns namespaceList
        }
        client = mockk(relaxed = true) {
            every { namespaces() } returns nonNamespaceOperation
        }
        /**
        client = mockk(relaxed = true) {
            every { namespaces() } returns
                mockk {
                    io.mockk.every { list() } returns
                            io.mockk.mockk {
                                io.mockk.every { items } returns
                                        kotlin.collections.emptyList()
                            }
                }
        }
        */

        model = KubernetesResourceModel(
            fun(resourceChange: ResourceChangeObservable): Cluster {
                return object: Cluster(resourceChange) {
                    override fun createClient(): NamespacedKubernetesClient {
                        return this@KubernetesResourceModelTest.client
                    }

                    override fun getWatchableProviders(client: NamespacedKubernetesClient): List<() -> WatchableResource> {
                        return return emptyList()
                    }
                }
            })
    }

    @After
    fun after() {
        unmockkAll()
    }

    @Test
    fun `should call list namespaces on client`() {
        // given
        // when
        model.getAllNamespaces()
        // then
        verify { client.namespaces().list().items }
    }
}
