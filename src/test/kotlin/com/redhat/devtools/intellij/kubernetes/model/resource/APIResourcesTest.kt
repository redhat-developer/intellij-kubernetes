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
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.apiResource
import io.fabric8.kubernetes.client.BaseClient
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import java.net.HttpURLConnection
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class APIResourcesTest {

    private val version = "v1"

    private val coreApiResourceList = """
        {
          "kind": "APIResourceList",
          "groupVersion": "$version",
          "resources": [
                {
                  "name": "pods",
                  "singularName": "",
                  "namespaced": true,
                  "kind": "Pod",
                  "verbs": [
                      "create",
                      "delete",
                      "deletecollection",
                      "get",
                      "list",
                      "patch",
                      "update",
                      "watch"
                  ],
                  "shortNames": [
                      "po"
                  ],
                  "categories": [
                      "all"
                  ],
                  "storageVersionHash": "xPOwRZ+Yhw8="
                },
                {
                    "name": "nodes",
                    "singularName": "",
                    "namespaced": false,
                    "kind": "Node",
                    "verbs": [
                        "create",
                        "delete",
                        "deletecollection",
                        "get",
                        "list",
                        "patch",
                        "update",
                        "watch"
                    ],
                    "shortNames": [
                        "no"
                    ],
                    "storageVersionHash": "XwShjMxG9Fs="
                }
          ]
        }
    """.trimIndent()

    private val extensionsApiResourceList = """
        {
          "kind": "APIResourceList",
          "apiVersion": "$version",
          "groupVersion": "serving.knative.dev/$version",
          "resources": [
                {
                  "name": "services",
                  "singularName": "service",
                  "namespaced": true,
                  "kind": "Service",
                  "verbs": [
                    "delete",
                    "deletecollection",
                    "get",
                    "list",
                    "patch",
                    "create",
                    "update",
                    "watch"
                  ],
                  "shortNames": [
                    "kservice",
                    "ksvc"
                  ],
                  "categories": [
                    "all",
                    "knative",
                    "serving"
                  ],
                  "storageVersionHash": "vqppxVmf8h0="
                },
                {
                  "name": "services/status",
                  "singularName": "",
                  "namespaced": true,
                  "kind": "Service",
                  "verbs": [
                    "get",
                    "patch",
                    "update"
                  ]
                },
                {
                  "name": "configurations",
                  "singularName": "configuration",
                  "namespaced": true,
                  "kind": "Configuration",
                  "verbs": [
                    "delete",
                    "deletecollection",
                    "get",
                    "list",
                    "patch",
                    "create",
                    "update",
                    "watch"
                  ],
                  "shortNames": [
                    "config",
                    "cfg"
                  ],
                  "categories": [
                    "all",
                    "knative",
                    "serving"
                  ],
                  "storageVersionHash": "wVwjm9dp8dc="
                }
          ]
        }
    """.trimIndent()

    private var coreResourcesCall: Call? = null // core resources api (pod, deployment, etc.)
    private var extensionResourcesCall: Call? = null // extension resources api (job, etc.)
    private var notFoundCall: Call? = null // call for unknown resources api
    private var httpClient: OkHttpClient? = null
    private var client: KubernetesClient? = null

    @Before
    fun before() {
        this.coreResourcesCall = createCall()
        this.extensionResourcesCall = createCall()
        this.notFoundCall = createCall()
        this.httpClient = createHttpClient()
        this.client = createClient(httpClient)
    }

    @Test
    fun `#get should return null if response is null`() {
        // given
        // when
        val apiResource = APIResources(client!!).get("bogusKind", "bogusGroup", "bogusVersion")
        // then
        assertThat(apiResource).isNull()
    }

    @Test
    fun `#get should return null if response has code 404`() {
        // given
        createResponseForCall(notFoundCall!!, "{}", HttpURLConnection.HTTP_NOT_FOUND)
        // when
        val apiResource = APIResources(client!!).get("bogusKind", "bogusGroup", "bogusVersion")
        // then
        assertThat(apiResource).isNull()
    }

    @Test(expected = KubernetesClientException::class)
    fun `#get should throw KubernetesClientException if response has code is NOT 200 NOR 404`() {
        // given
        createResponseForCall(notFoundCall!!, "{}", HttpURLConnection.HTTP_FORBIDDEN)
        // when
        APIResources(client!!).get("aKind", "aGroup", "aVersion")
        // then
    }

    @Test
    fun `#get for Pod should return null if unknown version`() {
        // given
        createResponseForCall(coreResourcesCall!!, coreApiResourceList, HttpURLConnection.HTTP_OK)
        // when
        val apiResource = APIResources(client!!).get("Pod", null, "vBogus")
        // then
        assertThat(apiResource).isNull()
    }

    @Test
    fun `#get for Pod should return pod APIResource`() {
        // given
        val expected = apiResource("pods", "", true, "Pod")
        createResponseForCall(coreResourcesCall!!, coreApiResourceList, HttpURLConnection.HTTP_OK)
        // when
        val found = APIResources(client!!).get("Pod", null, version)
        // then
        assertThat(found).isEqualTo(expected)
    }

    @Test
    fun `#get for knative Service should return knative Service APIResource`() {
        // given
        val expected = apiResource("services", "service", true, "Service")
        createResponseForCall(extensionResourcesCall!!, extensionsApiResourceList, HttpURLConnection.HTTP_OK)
        // when
        val found = APIResources(client!!).get("Service", "serving.knative.dev", version)
        // then
        assertThat(found).isEqualTo(expected)
    }

    @Test
    fun `#get for unknown kind should return null`() {
        // given
        createResponseForCall(extensionResourcesCall!!, coreApiResourceList, HttpURLConnection.HTTP_OK)
        // when
        val found = APIResources(client!!).get("Yoda", "rebels", version)
        // then
        assertThat(found).isNull()
    }

    private fun createCall(): Call {
        return mock()
    }

    private fun createResponseForCall(call: Call, body: String, responseCode: Int) {
        doReturn(createResponse(body, responseCode))
            .whenever(call).execute()
    }

    private fun createResponse(body: String, responseCode: Int): Response {
        val responseBody = createResponseBody(body)
        return mock {
            on { this.body } doReturn responseBody
            on { this.code } doReturn responseCode
        }
    }

    private fun createResponseBody(body: String): ResponseBody {
        return mock {
            on { bytes() } doReturn body.toByteArray()
            on { string() } doReturn body
        }
    }

    private fun createHttpClient(): OkHttpClient {
        return mock {
            on {
                newCall(any())
            } doAnswer {
                val request: Request = it.getArgument(0)
                val url = request.url.toString()
                when {
                    // core resources api
                    url.contains("/${APIResources.URL_API}/")
                                && url.endsWith("$version") ->
                        coreResourcesCall
                    // extension resources api
                    url.contains("/${APIResources.URL_APIS}/")
                                && url.endsWith("$version") ->
                        extensionResourcesCall
                    else ->
                        notFoundCall
                }
            }
        }
    }

    private fun createClient(httpClient: OkHttpClient?): KubernetesClient {
        val client = spy(DefaultKubernetesClient())
        doReturn(httpClient)
            .whenever(client as BaseClient).httpClient
        return client
    }
}