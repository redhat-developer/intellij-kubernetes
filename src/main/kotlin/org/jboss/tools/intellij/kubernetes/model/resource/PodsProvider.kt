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
package org.jboss.tools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import org.jboss.tools.intellij.kubernetes.model.WatchableResource
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier

class PodsProvider(client: KubernetesClient, namespace: String?)
    : NamespacedResourcesProvider<Pod, KubernetesClient>(client, namespace) {

    companion object {
        val KIND = Pod::class.java;
    }

    override val kind = KIND

    override fun loadAllResources(namespace: String): List<Pod> {
        return client.pods().inNamespace(namespace).list().items
    }

    override fun getWatchableResource(namespace: String): WatchableResourceSupplier? {
        return { client.pods().inNamespace(namespace) as WatchableResource }
    }
}
