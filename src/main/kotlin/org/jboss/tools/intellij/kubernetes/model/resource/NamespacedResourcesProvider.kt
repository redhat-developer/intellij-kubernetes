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

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Listable
import io.fabric8.kubernetes.client.dsl.Watchable

interface INamespacedResourcesProvider<T: HasMetadata>: IResourcesProvider<T> {
    var namespace: String?
}

abstract class NamespacedResourcesProvider<R : HasMetadata, C : KubernetesClient>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INamespacedResourcesProvider<R> {

    override var namespace: String? = null
        set(namespace) {
            invalidate()
            field = namespace
        }

    override fun getAllResources(): Collection<R> {
        if (allResources.isEmpty()) {
            if (namespace != null) {
                allResources.addAll(loadAllResources(namespace!!))
            }
        }
        return allResources
    }

    protected open fun loadAllResources(namespace: String): List<R> {
        return (getRetrieveOperation(namespace).invoke() as? Listable<KubernetesResourceList<R>>)?.list()?.items ?: emptyList()
    }

    override fun getRetrieveOperation(): () -> Watchable<Watch, Watcher<R>>? {
        if (namespace == null) {
            return { null }
        }
        return getRetrieveOperation (namespace!!)
    }

    protected abstract fun getRetrieveOperation(namespace: String): () -> Watchable<Watch, Watcher<R>>?

}
