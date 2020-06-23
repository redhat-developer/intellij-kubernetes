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

import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.AppsAPIGroupClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider

class DeploymentsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<Deployment, KubernetesClient>(client) {

    companion object {
        val KIND = Deployment::class.java;
    }

    override val kind = KIND

    private val appClient = client.adapt(AppsAPIGroupClient::class.java)

    override fun getRetrieveOperation(namespace: String): () -> Watchable<Watch, Watcher<Deployment>>? {
        return { appClient.deployments().inNamespace(namespace) }
    }

}
