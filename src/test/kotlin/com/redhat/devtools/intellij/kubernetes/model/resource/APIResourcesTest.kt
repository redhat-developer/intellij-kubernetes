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

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import io.fabric8.kubernetes.api.model.APIResourceBuilder
import io.fabric8.kubernetes.api.model.APIResourceList
import io.fabric8.kubernetes.api.model.APIResourceListBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
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

    private var client: ClientAdapter<out KubernetesClient> = mock()
    private var api: APIResources? = null

    @Before
    fun before() {
        this.client = createClient()
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

    private fun createClient(): ClientAdapter<out KubernetesClient> {
        val client: KubernetesClient = mock()
        mockCoreApiResources(version, client)
        mockExtensionApiResources(version, client)
        return KubeClientAdapter(client)
    }

    private fun mockCoreApiResources(version: String, client: KubernetesClient) {
        mockGetApiResources(coreApiResourceList, version, client)
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
        // ex. serving.knative.dev/v1
        mockGetApiResources(resourceList, "/$version", client)
    }

    private fun mockGetApiResources(resources: APIResourceList, groupVersion: String, client: KubernetesClient) {
        doReturn(resources)
            .whenever(client).getApiResources(argThat(ArgumentMatcher {
                it.endsWith(groupVersion)
            }))

    }
}