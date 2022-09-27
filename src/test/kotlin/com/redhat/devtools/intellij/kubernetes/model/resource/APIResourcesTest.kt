/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import io.fabric8.kubernetes.api.model.APIResourceBuilder
import io.fabric8.kubernetes.api.model.APIResourceList
import io.fabric8.kubernetes.api.model.APIResourceListBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.http.HttpClient
import io.fabric8.kubernetes.client.http.HttpRequest
import io.fabric8.kubernetes.client.http.HttpResponse
import io.fabric8.kubernetes.client.utils.Serialization
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher

class APIResourcesTest {

    private val version = "v1"

    private val podsApiResource = APIResourceBuilder()
        .withName("pods")
        .withSingularName("")
        .withNamespaced(true)
        .withKind("Pod")
        .withVerbs("create",
            "delete",
            "deletecollection",
            "get",
            "list",
            "patch",
            "update",
            "watch" )
        .withShortNames("po")
        .withCategories("all")
        .withStorageVersionHash("xPOwRZ+Yhw8=")
        .build()
    private val nodesApiResource = APIResourceBuilder()
        .withName("nodes")
        .withSingularName("")
        .withNamespaced(false)
        .withKind("Node")
        .withVerbs("create",
            "delete",
            "deletecollection",
            "get",
            "list",
            "patch",
            "update",
            "watch" )
        .withShortNames("no")
        .withStorageVersionHash("XwShjMxG9Fs")
        .build()

    private val coreApiResourceList = APIResourceListBuilder()
        .withKind("APIResourceList")
        .withGroupVersion(version)
        .withResources(podsApiResource, nodesApiResource)
        .build()

    private var response: HttpResponse<InputStream>? = null
    private var client: ClientAdapter<out KubernetesClient> = mock()
    private var api: APIResources? = null

    @Before
    fun before() {
        this.response = mock()
        val httpClient = createHttpClient(response!!)
        this.client = createClient(httpClient)
        this.api = APIResources(client)
    }

    @Test
    fun `#get should return null if unsupported version is requested`() {
        // given
        // when
        val apiResource = api!!.get("pod", null, "bogusVersion")
        // then
        assertThat(apiResource).isNull()
    }
    @Test
    fun `#get should return APIResource if Pod in existing version is requested`() {
        // given
        // when
        val found = api!!.get("Pod", null, version)
        // then
        assertThat(found).isEqualTo(podsApiResource)
    }

    @Test
    fun `#get for knative Service should return knative Service APIResource`() {
        // given
        val expected = APIResourceBuilder()
            .withName("services")
            .withSingularName("service")
            .withNamespaced(true)
            .withKind("Service")
            .build()
        // when
        val found = api!!.get("Service", "serving.knative.dev", version)
        // then
        assertThat(found).isEqualTo(expected)
    }


    @Test
    fun `#get for unknown kind should return null`() {
        // given
        // when
        val found = APIResources(client).get("Yoda", "rebels", version)
        // then
        assertThat(found).isNull()
    }

    private fun mockResponseContent(body: String, responseCode: Int, response: HttpResponse<InputStream>) {
        doReturn(ByteArrayInputStream(body.toByteArray()))
            .whenever(response).body()
        doReturn(responseCode)
            .whenever(response).code()
        doReturn(HttpResponse.isSuccessful(responseCode))
            .whenever(response).isSuccessful
    }

    private fun createHttpClient(response: HttpResponse<InputStream>): HttpClient {
        val request: HttpRequest = mock()
        val uriArgument = ArgumentCaptor.forClass(String::class.java)
        val builder: HttpRequest.Builder = mock {
            on { uri(uriArgument.capture()) } doReturn mock
            on { build() } doReturn request
        }
        val client: HttpClient = mock()
        doReturn(builder)
            .whenever(client).newHttpRequestBuilder()
        doAnswer {
            val uri = uriArgument.value
            if (uri != null && uri.endsWith("/api/$version")) {
                response
            } else {
                throw KubernetesClientException(
                    "did not request core resources at /api/$version",
                    HttpURLConnection.HTTP_NOT_FOUND,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            }
        }.whenever(client).send(any(), any<Class<InputStream>>())
        return client
    }

    private fun createClient(httpClient: HttpClient): ClientAdapter<out KubernetesClient> {
        val client = spy(DefaultKubernetesClient())
        doReturn(httpClient)
            .whenever(client).httpClient
        mockCoreApiResources(coreApiResourceList, response!!)
        mockExtensionApiResources(version, client)
        return KubeClientAdapter(client)
    }

    private fun mockCoreApiResources(apiResources: APIResourceList, response: HttpResponse<InputStream>) {
        mockResponseContent(Serialization.asJson(apiResources), HttpURLConnection.HTTP_OK, response)
    }

    private fun mockExtensionApiResources(version: String, client: KubernetesClient) {
        val services = APIResourceBuilder()
            .withName("services")
            .withSingularName("service")
            .withKind("Service")
            .withNamespaced(true)
            .build()
        val servicesStatus = APIResourceBuilder()
            .withName("services/status")
            .withSingularName("") // empty singular name
            .withKind("Service")
            .withNamespaced(true)
            .build()
        val configurations = APIResourceBuilder()
            .withName("configurations")
            .withSingularName("configuration")
            .withKind("Configuration")
            .withNamespaced(true)
            .build()
        val resourceList = APIResourceListBuilder()
            .withKind("APIResourceList")
            .withApiVersion(version)
            .addToResources(services)
            .addToResources(servicesStatus)
            .addToResources(configurations)
            .build()
        doReturn(resourceList)
            .whenever(client).getApiResources(argThat(ArgumentMatcher {
                it.endsWith("/$version") // ex. serving.knative.dev/v1
            }))
    }
}