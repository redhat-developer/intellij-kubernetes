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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import org.junit.Test

class KubernetesResourceModelTest {

    @Test
    fun `should call list namespaces on client`() {
        // given
        mockClient(emptyList())
        // when
        KubernetesResourceModel.getAllNamespaces()
        // then
        verify { anyConstructed<DefaultKubernetesClient>().namespaces() }
    }

    private fun mockClient(namespaces: List<Namespace>) {
        // client.namespaces().list().items.asSequence()
        mockkConstructor(DefaultKubernetesClient::class)
        every { anyConstructed<DefaultKubernetesClient>().namespaces() } returns
                mockk {
                    every { list() } returns
                            mockk {
                                every { items } returns
                                        namespaces
                            }
                    every { watch(any()) } returns
                            mockk()
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
}