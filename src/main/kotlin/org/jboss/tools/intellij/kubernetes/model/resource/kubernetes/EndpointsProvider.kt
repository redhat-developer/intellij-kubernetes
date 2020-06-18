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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes

import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider

class EndpointsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<Endpoints, KubernetesClient>(client) {

    companion object {
        val KIND = Endpoints::class.java;
    }

    override val kind = KIND

    override fun loadAllResources(namespace: String): List<Endpoints> {
        return client.endpoints().inNamespace(namespace).list().items
    }

    override fun getWatchableResource(namespace: String): () -> Watchable<Watch, Watcher<Endpoints>>? {
        return { client.endpoints().inNamespace(namespace) }
    }

}
