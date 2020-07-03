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

import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.client.AppsAPIGroupClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.AdaptedClient
import org.jboss.tools.intellij.kubernetes.model.IAdaptedClient
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider

class StatefulSetsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<StatefulSet, KubernetesClient>(client),
        IAdaptedClient<AppsAPIGroupClient> by AdaptedClient(client, AppsAPIGroupClient::class.java) {

    companion object {
        val KIND = StatefulSet::class.java
    }

    override val kind = KIND

    override fun getLoadOperation(namespace: String): () -> Watchable<Watch, Watcher<StatefulSet>>? {
        return { adaptedClient.statefulSets().inNamespace(namespace) }
    }
}
