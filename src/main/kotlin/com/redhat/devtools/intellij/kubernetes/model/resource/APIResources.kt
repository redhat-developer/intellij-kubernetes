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
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.utils.ApiVersionUtil

/**
 * Class that allows API discovery by querying cluster api resources.
 */
class APIResources(private val client: ClientAdapter<out KubernetesClient>) {

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
        val resources = client.get().getApiResources(ApiVersionUtil.joinApiGroupAndVersion(group, version))?.resources
            ?: return null
        return getByKind(kind, resources)
    }

    private fun getByKind(kind: String, resources: List<APIResource>): APIResource? {
        val plural = Pluralize.toPlural(kind).toLowerCase()
        return resources.firstOrNull {
            plural == it.name
        }
    }
}
