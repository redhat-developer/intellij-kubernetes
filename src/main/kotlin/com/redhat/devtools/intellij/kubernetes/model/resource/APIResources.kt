/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import io.fabric8.kubernetes.api.Pluralize
import io.fabric8.kubernetes.api.model.APIResource
import io.fabric8.kubernetes.api.model.APIResourceList
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.OperationSupport
import io.fabric8.kubernetes.client.utils.ApiVersionUtil

/**
 * Class that allows API discovery by querying cluster api resources.
 * Can be re-implemented via DefaultKubernetesClient.getApiResources(String) as per kubernetes-client 5.11.0
 */
class APIResources(private val client: ClientAdapter<out KubernetesClient>) {

    companion object {
        const val PATH_API = "/api"
    }

    /**
     * Returns the [APIResource] for the given kind, group and version.
     * Returns `null` if it doesn't exist.
     * The cluster is queried for the existing api resources.
     *
     * @param kind the kind of the resource
     * @param group the api group of the resource
     * @param version the version of the resource
     * @return [APIResource] for the given kind, group and version
     * @throws KubernetesClientException
     */
    fun get(kind: String, group: String?, version: String): APIResource? {
        val resources =
            if (group == null) {
                requestCoreResources(version, client)
            } else {
                requestExtensionResources(group, version, client)
            }
        return getByKind(kind, resources)
    }

    private fun requestExtensionResources(group: String, version: String, client: ClientAdapter<out KubernetesClient>): List<APIResource> {
        return client.get().getApiResources(
            ApiVersionUtil.joinApiGroupAndVersion(group, version))?.resources
            ?: emptyList()
    }

    /**
     * Returns the core resources. Returns an empty list if none are found.
     * **Note:** this "workaround" will be obsolete once
     * (fix #4065: use Client.getAPIResources("v1") for core/legacy resources )[https://github.com/fabric8io/kubernetes-client/pull/4066]
     * lands in a release (6.0 expected)
     */
    private fun requestCoreResources(version: String, client: ClientAdapter<out KubernetesClient>): List<APIResource> {
        return OperationSupport(client.get().httpClient, client.config.configuration)
            .restCall(APIResourceList::class.java, PATH_API, version)?.resources
            ?: emptyList()
    }

    private fun getByKind(kind: String, resources: List<APIResource>): APIResource? {
        val plural = Pluralize.toPlural(kind).toLowerCase()
        return resources.firstOrNull {
            plural == it.name
        }
    }
}
