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
package com.redhat.devtools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.fabric8.kubernetes.client.AppsAPIGroupClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.OpenShiftClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClientsTest {

    @Test
    fun `#isOpenShift should return true if has OpenShiftClient`() {
        // given
        val clients = createClients(mock<OpenShiftClient>())
        // when
        val isOpenShift = clients.isOpenShift()
        // then
        assertThat(isOpenShift).isTrue()
    }

    @Test
    fun `#isOpenShift should return false if has KubernetesClient`() {
        // given
        val clients = createClients(mock<KubernetesClient>())
        // when
        val isOpenShift = clients.isOpenShift()
        // then
        assertThat(isOpenShift).isFalse()
    }

    @Test
    fun `#get() should return client`() {
        // given
        val client = mock<KubernetesClient>()
        val clients = createClients(client)
        // when
        val returned = clients.get()
        // then
        assertThat(returned).isEqualTo(client)
    }

    @Test
    fun `#get(type) should adapt client`() {
        // given
        val adapted = mock<AppsAPIGroupClient>()
        val client = mock<KubernetesClient> {
            on { adapt(AppsAPIGroupClient::class.java) } doReturn adapted
        }
        val clients = createClients(client)
        // when
        clients.get(AppsAPIGroupClient::class.java)
        // then
        verify(client).adapt(AppsAPIGroupClient::class.java)
    }

    @Test
    fun `#close should close client`() {
        // given
        val client = mock<KubernetesClient>()
        val clients = createClients(client)
        // when
        clients.close()
        // then
        verify(client).close()
    }

    @Test
    fun `#close should close adapted clients`() {
        // given
        val adapted = mock<AppsAPIGroupClient>()
        val client = mock<KubernetesClient> {
            on { adapt(AppsAPIGroupClient::class.java) } doReturn adapted
        }
        val clients = createClients(client)
        clients.get(AppsAPIGroupClient::class.java) // create adapted client
        // when
        clients.close()
        // then
        verify(adapted).close()
    }

    private fun <T : KubernetesClient> createClients(client: T): Clients<T> {
        return Clients(client)
    }

}