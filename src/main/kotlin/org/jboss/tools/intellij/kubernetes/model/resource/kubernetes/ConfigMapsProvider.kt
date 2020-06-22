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

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider

class ConfigMapsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<ConfigMap, KubernetesClient>(client) {

    companion object {
        val KIND = ConfigMap::class.java;
    }

    override val kind = KIND

    override fun getRetrieveOperation(namespace: String): () -> Watchable<Watch, Watcher<ConfigMap>>? {
        return { client.configMaps().inNamespace(namespace) }
    }

}
