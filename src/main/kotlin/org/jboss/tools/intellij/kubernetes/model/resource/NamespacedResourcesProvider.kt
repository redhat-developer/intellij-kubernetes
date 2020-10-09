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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch

interface INamespacedResourcesProvider<T: HasMetadata>: IResourcesProvider<T> {
    var namespace: String?
}

abstract class NamespacedResourcesProvider<R : HasMetadata, C : KubernetesClient>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INamespacedResourcesProvider<R> {

    constructor(namespace: String?, client: C): this(client) {
        this.namespace = namespace
    }

    final override var namespace: String? = null
        set(namespace) {
            logger<NamespacedResourcesProvider<*,*>>().debug("Using new namespace $namespace.")
            invalidate()
            field = namespace
        }

    override fun getAllResources(): Collection<R> {
        synchronized(allResources) {
            if (allResources.isEmpty()) {
                if (namespace != null) {
                    allResources.addAll(loadAllResources(namespace!!))
                } else {
                    logger<NamespacedResourcesProvider<*,*>>().debug("Could not load $kind resources: no namespace set.")
                }
            }
            return allResources
        }
    }

    protected open fun loadAllResources(namespace: String): List<R> {
        logger<NamespacedResourcesProvider<*,*>>().debug("Loading $kind resources.")
        return getOperation(namespace).invoke()?.list()?.items ?: emptyList()
    }

    override fun getWatchable(): () -> Watchable<Watch, Watcher<R>>? {
        if (namespace == null) {
            logger<NamespacedResourcesProvider<*,*>>().debug("Returned empty watch for $kind: no namespace set.")
            return { null }
        }
        return getOperation(namespace!!)
    }

    protected open fun getOperation(namespace: String): () -> WatchableAndListable<R> {
        return { null }
    }

}
