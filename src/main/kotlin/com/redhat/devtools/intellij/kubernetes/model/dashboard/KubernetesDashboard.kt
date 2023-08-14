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

import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PodForService
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.LocalPortForward

/**
 * A factory that can determine the url the dashboard for a given Kubernetes cluster.
 * Based on the implementation in minikube 1.30.1 at https://github.com/kubernetes/minikube/blob/master/cmd/minikube/cmd/dashboard.go#L206
 */
class KubernetesDashboard(
    client: KubernetesClient,
    contextName: String,
    clusterUrl: String?,
    /* for testing purposes */
    httpRequest: HttpRequest = HttpRequest()
): AbstractDashboard<KubernetesClient>(client, contextName, clusterUrl, httpRequest) {

    companion object {
        private const val NAMESPACE = "kubernetes-dashboard"
        private const val SERVICE_NAME = "kubernetes-dashboard"
    }

    private var portForward: LocalPortForward? = null

    override fun doConnect(): HttpRequest.HttpStatusCode? {
        val service = getDashboardService(client) ?: return null
        val pod = getPod(service, client) ?: return null
        val portForward = portForward(pod, client) ?: return null
        this.portForward = portForward
        return httpRequest.request("localhost", portForward.localPort)
    }

    private fun getDashboardService(client: KubernetesClient): Service? {
        return client
            .services()
            .inNamespace(NAMESPACE)
            .withName(SERVICE_NAME)
            .get()
    }

    private fun getPod(dashboard: Service, client: KubernetesClient): Pod? {
        return client
            .pods()
            .inNamespace(NAMESPACE)
            .list().items
            .find { pod -> PodForService(dashboard).test(pod) }
    }

    private fun portForward(pod: Pod, client: KubernetesClient): LocalPortForward? {
        val containerPort = pod.spec?.containers?.get(0)?.ports?.get(0)?.containerPort ?: return null
        val portForward = client
            .pods()
            .inNamespace(pod.metadata.namespace)
            .withName(pod.metadata.name)
            .portForward(containerPort)
        val serverError = portForward.serverThrowables.firstOrNull()
        if (serverError != null) {
            throw serverError
        }
        val clientError = portForward.serverThrowables.firstOrNull()
        if (clientError != null) {
            throw clientError
        }
        return portForward
    }

    override fun close() {
        portForward?.close()
    }
}