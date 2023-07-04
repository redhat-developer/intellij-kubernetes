/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.dashboard

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.util.PluginException
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerPort
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.LocalPortForward
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.PodResource
import io.fabric8.kubernetes.client.dsl.ServiceResource
import java.net.HttpURLConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test


class KubernetesDashboardTest {

    companion object {
        private const val DASHBOARD_NAMESPACE = "kubernetes-dashboard"
        private const val DASHBOARD_SERVICE_NAME = "kubernetes-dashboard"
        private const val DASHBOARD_POD_NAME = "kubernetes-dashboard"
        private const val DASHBOARD_APP_KEY = "k8s-app"
        private const val DASHBOARD_LABEL = "kubernetes-dashboard"
    }

    private var client: KubernetesClient = mock()
    private val dashboard = KubernetesDashboard(client, "yoda", "https://localhost")

    @Before
    fun before() {
        this.client = client(NAMESPACE2.metadata.name, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
    }

    @Test
    fun `#get should NOT connect a 2nd time if 1st connect was successful`() {
        // given
        val dashboard = FixedResponseDashboard(HttpURLConnection.HTTP_OK)
        dashboard.get()
        // when
        dashboard.get()
        // then
        assertThat(dashboard.connectInvoked).isEqualTo(1)
    }

    @Test
    fun `#get should connect a 2nd time if 1st connect was unsuccessful`() {
        // given
        val dashboard = FixedResponseDashboard(HttpURLConnection.HTTP_NOT_FOUND)
        try {
            dashboard.get()
        } catch(e: PluginException) {
            // will throw because unsuccessful. Ignore it.
        }
        // when
        try {
            dashboard.get()
        } catch(e: PluginException) {
            // will throw because unsuccessful. Ignore it.
        }
        // then
        assertThat(dashboard.connectInvoked).isEqualTo(2)
    }

    @Test(expected = PluginException::class)
    fun `#get should throw if accessing cluster throws`() {
        // given
        doThrow(KubernetesClientException::class)
            .whenever(client).services()
        // when
        dashboard.get()
        // then
    }

    @Test(expected = PluginException::class)
    fun `#get should throw if service cannot be found`() {
        // given
        // service doesn't exist
        // when
        dashboard.get()
        // then
    }

    @Test(expected = PluginException::class)
    fun `#get should throw if pod cannot be found`() {
        // given
        // service exists, pod doesn't exist
        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        // when
        dashboard.get()
        // then
    }

    @Test(expected=PluginException::class)
    fun `#get should return url if port forward has server errors`() {
        // given
        val expectedPort = 9090
        val expectedUrl = "https://localhost:$expectedPort"
        val dashboardHttpStatusCode = HttpURLConnection.HTTP_FORBIDDEN

        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        val dashboardPod = dashboardPod(8080)
        val serverError = KubernetesClientException("dark side of the force was used")
        val portForward = portForward(expectedPort, listOf(serverError))
        mockDashboardPodAndPortForward(dashboardPod, portForward, client)

        val dashboard = createKubernetesDashboard(expectedUrl, dashboardHttpStatusCode)

        // when
        dashboard.get()
        // then
    }

    @Test
    fun `#get should return url if dashboard responds with 200 OK`() {
        // given
        val expectedPort = 9090
        val expectedUrl = "https://localhost:$expectedPort"
        val dashboardHttpStatusCode = HttpURLConnection.HTTP_OK

        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        val dashboardPod = dashboardPod(8080)
        val portForward = portForward(expectedPort)
        mockDashboardPodAndPortForward(dashboardPod, portForward, client)

        val dashboard = createKubernetesDashboard(expectedUrl, dashboardHttpStatusCode)
        // when
        val effectiveUrl = dashboard.get()
        // then
        assertThat(effectiveUrl).isEqualTo(expectedUrl)
    }

    @Test
    fun `#get should return url if dashboard responds with 403 FORBIDDEN`() {
        // given
        val expectedPort = 9090
        val expectedUrl = "https://localhost:$expectedPort"
        val dashboardHttpStatusCode = HttpURLConnection.HTTP_FORBIDDEN

        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        val dashboardPod = dashboardPod(8080)
        val portForward = portForward(expectedPort)
        mockDashboardPodAndPortForward(dashboardPod, portForward, client)

        val dashboard = createKubernetesDashboard(expectedUrl, dashboardHttpStatusCode)

        // when
        val effectiveUrl = dashboard.get()
        // then
        assertThat(effectiveUrl).isEqualTo(expectedUrl)
    }

    @Test(expected = PluginException::class)
    fun `#get should throw if dashboard responds with 500 Internal Server Error`() {
        // given
        val expectedPort = 9090
        val expectedUrl = "https://localhost:$expectedPort"
        val dashboardHttpStatusCode = HttpURLConnection.HTTP_INTERNAL_ERROR

        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        val dashboardPod = dashboardPod(8080)
        val portForward = portForward(expectedPort)
        mockDashboardPodAndPortForward(dashboardPod, portForward, client)

        val dashboard = createKubernetesDashboard(expectedUrl, dashboardHttpStatusCode)
        // when
        dashboard.get()
        // then throws
    }

    @Test
    fun `#close should close existing portforward`() {
        // given
        val expectedPort = 9090
        val expectedUrl = "https://localhost:$expectedPort"
        val dashboardHttpStatusCode = HttpURLConnection.HTTP_OK

        val dashboardService = dashboardService()
        mockDashboardService(dashboardService, client)

        val dashboardPod = dashboardPod(8080)
        val portForward = portForward(expectedPort)
        mockDashboardPodAndPortForward(dashboardPod, portForward, client)

        val dashboard = createKubernetesDashboard(expectedUrl, dashboardHttpStatusCode)
        dashboard.get() // create port forward
        // when
        dashboard.close()
        // then throws
        verify(portForward).close()
    }

    private fun mockDashboardService(dashboardService: Service, client: KubernetesClient) {
        val serviceResource: ServiceResource<Service> = mock {
            on { get() } doReturn dashboardService
        }
        val servicesInNamespace: NonNamespaceOperation<Service, ServiceList, ServiceResource<Service>> = mock {
            on { withName(DASHBOARD_SERVICE_NAME) } doReturn serviceResource
        }
        val services: MixedOperation<Service, ServiceList, ServiceResource<Service>> = mock {
            on { inNamespace(DASHBOARD_NAMESPACE) } doReturn servicesInNamespace
        }
        whenever(client.services())
            .thenReturn(services)
    }

    private fun mockDashboardPodAndPortForward(
        dashboardPod: Pod,
        portForward: LocalPortForward,
        client: KubernetesClient
    ) {
        val allPods: PodList = mock {
            on { items } doReturn listOf(POD1, POD2, dashboardPod, POD3)
        }
        val podResource: PodResource = mock {
            on { portForward(any()) } doReturn portForward
        }
        val podsInNamespace: NonNamespaceOperation<Pod, PodList, PodResource> = mock {
            on { list() } doReturn allPods
            on { withName(any()) } doReturn podResource
        }
        val pods: MixedOperation<Pod, PodList, PodResource> = mock {
            on { inNamespace(any()) } doReturn podsInNamespace
        }
        whenever(client.pods())
            .thenReturn(pods)
    }

    private fun portForward(
        localPort: Int,
        serverThrowables: List<Throwable> = emptyList(),
        clientThrowables: List<Throwable> = emptyList()
    ): LocalPortForward {
        return mock {
            on { getLocalPort() } doReturn localPort
            on { getServerThrowables() } doReturn serverThrowables
            on { getClientThrowables() } doReturn clientThrowables
        }
    }

    private fun dashboardService(): Service {
        val spec: ServiceSpec = mock {
            on { selector } doReturn mapOf(DASHBOARD_APP_KEY to DASHBOARD_LABEL)
        }
        return resource<Service>(DASHBOARD_SERVICE_NAME, DASHBOARD_NAMESPACE).apply {
            doReturn(spec)
                .whenever(this).spec
        }
    }

    private fun dashboardPod(port: Int): Pod {
        val containerPort: ContainerPort = mock {
            on { containerPort } doReturn port
        }
        val container: Container = mock {
            on { ports } doReturn listOf(containerPort)
        }
        val podSpec: PodSpec = mock {
            on { containers } doReturn listOf(container)
        }
        val metadata: ObjectMeta = mock {
            on { name } doReturn DASHBOARD_POD_NAME
            on { namespace } doReturn DASHBOARD_NAMESPACE
            on { labels } doReturn mapOf(DASHBOARD_APP_KEY to DASHBOARD_LABEL)
        }
        return mock {
            on { getMetadata() } doReturn metadata
            on { spec } doReturn podSpec
        }
    }

    private fun createKubernetesDashboard(expectedUrl: String, httpStatusCode: Int): KubernetesDashboard {
        val httpRequest: HttpRequest = mockHttpRequest(expectedUrl, httpStatusCode)
        return KubernetesDashboard(client, "yoda", "Dagobah", httpRequest)
    }

    private fun mockHttpRequest(url: String, code: Int): HttpRequest {
        val httpStatusCode: HttpRequest.HttpStatusCode = HttpRequest.HttpStatusCode(
            url,
            code
        )
        val httpRequest: HttpRequest = mock {
            on { request(any(), any()) } doReturn httpStatusCode
        }
        return httpRequest
    }

    private class FixedResponseDashboard(private val httpStatusCode: Int): AbstractDashboard<KubernetesClient>(
        mock(),
        "luke",
        "Tatooine",
        mock<HttpRequest>()
    ) {
        var connectInvoked = 0
        override fun doConnect(): HttpRequest.HttpStatusCode? {
            connectInvoked++
            // success
            return HttpRequest.HttpStatusCode("http://localhost", httpStatusCode)
        }
    }
}