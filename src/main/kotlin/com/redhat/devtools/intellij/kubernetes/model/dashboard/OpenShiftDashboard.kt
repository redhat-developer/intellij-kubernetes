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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.client.OpenShiftClient


/**
 * A factory that can determine the url of the dashboard for a given OpenShift cluster.
 *
 * Inspired by the implementation in minikube 1.30.1 at https://github.com/kubernetes/minikube/blob/master/cmd/minikube/cmd/dashboard.go#L206
 * Tested with:
 * - Red Hat CodeReady Containers
 * - Red Hat Developer Sandbox
 * - Red Hat OpenShift 3.11
 */
class OpenShiftDashboard(
    client: OpenShiftClient,
    contextName: String,
    clusterUrl: String?,
    /* for testing purposes */
    httpRequest: HttpRequest = HttpRequest()
) : AbstractDashboard<OpenShiftClient>(client, contextName, clusterUrl, httpRequest) {

    companion object {
        private const val NAMESPACE = "openshift-config-managed"
        private const val CONFIGMAP_NAME = "console-public"
        private const val CONFIGMAP_PROPERTY = "consoleURL"
    }

    override fun doConnect(): HttpRequest.HttpStatusCode? {
        return try {
            // OpenShift 4
            val configMap = getDashboardConfigMap(client) ?: return null
            val url = configMap.data[CONFIGMAP_PROPERTY] ?: return null
            httpRequest.request(url)
        } catch (e: KubernetesClientException) {
            // OpenShift 3
            logger<OpenShiftDashboard>().debug(
                "Could not access config map $CONFIGMAP_NAME in namespace $NAMESPACE.", e
            )
            val hostName = client.masterUrl.toExternalForm() ?: return null
            httpRequest.request("$hostName")
        }
    }

    private fun getDashboardConfigMap(client: KubernetesClient): ConfigMap? {
        return client
            .configMaps()
            .inNamespace(NAMESPACE)
            .withName(CONFIGMAP_NAME)
            .get()
    }
}