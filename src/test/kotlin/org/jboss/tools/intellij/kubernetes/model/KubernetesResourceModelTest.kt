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

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.mockk.*
import org.junit.Test

class KubernetesResourceModelTest {

    @Test
    fun `should call list namespaces on client`() {
        // given
        mockClient(emptyList())
//        mockResourceModel(client)
        // when
        KubernetesResourceModel.getAllNamespaces()
        // then
        verify { anyConstructed<DefaultKubernetesClient>().namespaces().list().items }
    }

    private fun mockResourceModel(client: NamespacedKubernetesClient) {
/**
        mockkObject(KubernetesResourceModel, recordPrivateCalls = true)
        every { KubernetesResourceModel invokeNoArgs "createClient" } returns client
        every { KubernetesResourceModel invoke
                "createWatch" withArguments (listOf({ resource: HasMetadata -> Unit }, { resource: HasMetadata -> Unit }))
            } returns mockk<KubernetesResourceWatch>() {
            every { start(any()) } returns Unit
        }
*/
    }

    private fun mockClient(namespaces: List<Namespace>) {
        // client.namespaces().list().items.asSequence()
        mockkConstructor(DefaultKubernetesClient::class)
        every { anyConstructed<DefaultKubernetesClient>().namespaces() } returns
                spyk {
                    every { list() } returns
                            spyk {
                                every { items } returns
                                        namespaces
                            }
                    every { watch(any()) } returns
                            mockk()
                }
/**
        mockkConstructor(NamespaceList::class)
        every { anyConstructed<NamespaceList>().getItems() } returns namespaces
        mockkConstructor(NamespaceOperationsImpl::class)
        every { anyConstructed<NamespaceOperationsImpl>().list() } returns spyk()
        every { anyConstructed<DefaultKubernetesClient>().namespaces() } returns
                mockk() {
                    every { list() } returns
                            spyk()
                    every { watch(any()) } returns
                            spyk()
                }
*/
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
}