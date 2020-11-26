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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import java.util.function.Supplier

interface INamespacedResourcesProvider<R: HasMetadata, C: Client>: IResourcesProvider<R> {
    var namespace: String?
}

abstract class NamespacedResourcesProvider<R : HasMetadata, C: Client>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INamespacedResourcesProvider<R, C> {

    constructor(namespace: String?, client: C): this(client) {
        this.namespace = namespace
    }

    final override var namespace: String? = null
        set(namespace) {
            logger<NamespacedResourcesProvider<*, *>>().debug("Using new namespace $namespace.")
            invalidate()
            field = namespace
        }

    override val allResources: List<R>
        get() {
            synchronized(_allResources) {
                if (_allResources.isEmpty()) {
                    if (namespace != null) {
                        _allResources.addAll(loadAllResources(namespace!!))
                    } else {
                        logger<NamespacedResourcesProvider<*, *>>().debug("Could not load $kind resources: no namespace set.")
                    }
                }
                return _allResources
            }
        }


    protected open fun loadAllResources(namespace: String): List<R> {
        logger<NamespacedResourcesProvider<*, *>>().debug("Loading $kind resources in namespace $namespace.")
        return getOperation(namespace).get()?.list()?.items ?: emptyList()
    }

    override fun getWatchable(): Supplier<Watchable<Watcher<R>>?> {
        if (namespace == null) {
            logger<NamespacedResourcesProvider<*, *>>().debug("Returned empty watch for $kind: no namespace set.")
            return Supplier { null }
        }
        return getOperation(namespace!!) as Supplier<Watchable<Watcher<R>>?>
    }

    protected open fun getOperation(namespace: String): Supplier<WatchableListableDeletable<R>> {
        return Supplier { null }
    }

    override fun delete(resources: List<HasMetadata>): Boolean {
        if (namespace == null) {
            return false
        }
        val toDelete = resources as? List<R> ?: return false
        return getOperation(namespace!!).get()?.delete(toDelete) ?: false
    }

}
