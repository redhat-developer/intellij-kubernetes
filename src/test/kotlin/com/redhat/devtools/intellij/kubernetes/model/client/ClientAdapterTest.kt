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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.config
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.namedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.http.HttpClient
import io.fabric8.kubernetes.client.impl.AppsAPIGroupClient
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.cert.X509Certificate
import java.util.function.Consumer
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

class ClientAdapterTest {


    private val certificate: X509Certificate = mock {
        on { subjectX500Principal } doReturn mock()
    }
    private val trustManager: X509TrustManager = mock {
        on { acceptedIssuers } doReturn arrayOf(certificate)
    }
    private val trustManagerProvider: (toIntegrate: List<X509ExtendedTrustManager>) -> X509TrustManager = mock {
        on { invoke(any()) } doReturn trustManager
    }

    private val createConfig: (context: String?) -> Config = { _ -> mock() }

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
    fun `#toOpenShift should return same instance if it's already a OSClientAdapter`() {
        // given
        val clientAdapter = OSClientAdapter(mock(), mock())
        // when
        val openShiftAdapter = clientAdapter.toOpenShift()
        // then
        assertThat(openShiftAdapter).isEqualTo(clientAdapter)
    }

    @Test
    fun `#toOpenShift should return adapt fabric8 client and create new OSClientAdapter`() {
        // given
        val osClient = mock<NamespacedOpenShiftClient>()
        val client = mock<KubernetesClient> {
            on { adapt(NamespacedOpenShiftClient::class.java) } doReturn osClient
        }
        val clientAdapter = KubeClientAdapter(client)
        // when
        val openShiftAdapter = clientAdapter.toOpenShift()
        // then
        verify(client).adapt(NamespacedOpenShiftClient::class.java)
        assertThat(openShiftAdapter.get()).isEqualTo(osClient)
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
        val config = config(ctx1, listOf(ctx1, ctx1))
        val createConfig = { _: String? -> config }
        val clientBuilder = createClientBuilder(false)
        // when
        ClientAdapter.Factory.create(namespace,  ctx1.name, clientBuilder, createConfig, trustManagerProvider)
        // then
        verify(config).namespace = namespace
        verify(config.currentContext.context).namespace = namespace
    }

    @Test
    fun `#create should call trust manager provider`() {
        // given
        val clientBuilder = createClientBuilder(false)
        // when
        ClientAdapter.Factory.create("namespace", "context", clientBuilder, createConfig, trustManagerProvider)
        // then
        verify(trustManagerProvider).invoke(any())
    }

    @Test
    fun `#create should return KubeClientAdapter if cluster is Kubernetes`() {
        // given
        val clientBuilder = createClientBuilder(false)
        // when
        val adapter = ClientAdapter.Factory.create("namespace", "context", clientBuilder, createConfig, trustManagerProvider)
        // then
        assertThat(adapter).isInstanceOf(KubeClientAdapter::class.java)
    }

    @Test
    fun `#create should return KubeClientAdapter if cluster is OpenShift`() {
        // given
        val clientBuilder = createClientBuilder(true)
        // when
        val adapter = ClientAdapter.Factory.create("namespace", "context", clientBuilder, createConfig, trustManagerProvider)
        // then
        assertThat(adapter).isInstanceOf(KubeClientAdapter::class.java)
    }

    @Suppress("SameParameterValue", "UNCHECKED_CAST")
    private fun createClientBuilder(isOpenShiftCluster: Boolean): KubernetesClientBuilder {

        val config = mock<Config>()
        val osClient = mock<NamespacedOpenShiftClient>()

        val k8client = mock<KubernetesClient> {
            on { adapt(any<Class<NamespacedOpenShiftClient>>()) } doReturn osClient
            on { hasApiGroup(any(), any()) } doReturn isOpenShiftCluster
            on { configuration } doReturn config
        }

        val httpClientBuilder = mock<HttpClient.Builder>()

        val builder =  mock<KubernetesClientBuilder> {
            on { withConfig(any<Config>())} doReturn mock
            on { build() } doReturn k8client
        }
        /* invoke consumer given to method */
        whenever(builder.withHttpClientBuilderConsumer(any())).thenAnswer {
            val consumer = it.arguments[0] as Consumer<HttpClient.Builder>
            consumer.accept(httpClientBuilder)
            builder
        }
        return builder

    }
}