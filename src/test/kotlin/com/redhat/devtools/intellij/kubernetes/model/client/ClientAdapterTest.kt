/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.client

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.config
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import io.fabric8.kubernetes.client.AppsAPIGroupClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClientAdapterTest {

    @Test
    fun `#isOpenShift should return true if has OpenShiftClient`() {
        // given
        val clientAdapter = OSClientAdapter(mock(), mock())
        // when
        val isOpenShift = clientAdapter.isOpenShift()
        // then
        assertThat(isOpenShift).isTrue
    }

    @Test
    fun `#isOpenShift should return false if has KubernetesClient`() {
        // given
        val clientAdapter = KubeClientAdapter(mock())
        // when
        val isOpenShift = clientAdapter.isOpenShift()
        // then
        assertThat(isOpenShift).isFalse()
    }

    @Test
    fun `#get() should return client`() {
        // given
        val client = mock<NamespacedKubernetesClient>()
        val clientAdapter = KubeClientAdapter(client)
        // when
        val returned = clientAdapter.get()
        // then
        assertThat(returned).isEqualTo(client)
    }

    @Test
    fun `#get(type) should adapt client`() {
        // given
        val adapted = mock<AppsAPIGroupClient>()
        val client = mock<NamespacedKubernetesClient> {
            on { adapt(AppsAPIGroupClient::class.java) } doReturn adapted
        }
        val clientAdapter = KubeClientAdapter(client)
        // when
        clientAdapter.get(AppsAPIGroupClient::class.java)
        // then
        verify(client).adapt(AppsAPIGroupClient::class.java)
    }

    @Test
    fun `#close should close client`() {
        // given
        val client = mock<NamespacedKubernetesClient>()
        val clientAdapter = KubeClientAdapter(client)
        // when
        clientAdapter.close()
        // then
        verify(client).close()
    }

    @Test
    fun `#close should close adapted clients`() {
        // given
        val adapted = mock<AppsAPIGroupClient>()
        val client = mock<NamespacedKubernetesClient> {
            on { adapt(AppsAPIGroupClient::class.java) } doReturn adapted
        }
        val clientAdapter = KubeClientAdapter(client)
        clientAdapter.get(AppsAPIGroupClient::class.java) // create adapted client
        // when
        clientAdapter.close()
        // then
        verify(adapted).close()
    }

    @Test
    fun `#create should set given namespace to client config`() {
        // given
        val namespace = "Crevasse City"
        val ctx1 = namedContext("Aldeeran", "Aldera", "Republic", "Organa" )
        val ctx2 = namedContext("Death Start", "Navy Garrison", "Empire", "Darh Vader" )
        val config = config(ctx1, listOf(ctx1, ctx2))
        // when
        ClientAdapter.Factory.create(namespace, config)
        // then
        verify(config).namespace = namespace
        verify(config.currentContext.context).namespace = namespace
    }

}