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
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import java.util.function.Supplier

interface INonNamespacedResourcesProvider<R: HasMetadata, C: Client>: IResourcesProvider<R>

abstract class NonNamespacedResourcesProvider<R : HasMetadata, C : Client>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INonNamespacedResourcesProvider<R, C> {

    override val allResources: List<R>
        get() {
            synchronized(_allResources) {
                if (_allResources.isEmpty()) {
                    _allResources.addAll(loadAllResources())
                }
                return _allResources
            }
        }

    protected open fun loadAllResources(): List<R> {
        logger<NamespacedResourcesProvider<*, *>>().debug("Loading all $kind resources.")
        return getOperation().get()?.list()?.items ?: emptyList()
    }

    override fun getWatchable(): Supplier<Watchable<Watcher<R>>?> {
        @Suppress("UNCHECKED_CAST")
        return getOperation() as Supplier<Watchable<Watcher<R>>?>
    }

    override fun delete(resources: List<HasMetadata>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val toDelete = resources as? List<R> ?: return false
        return getOperation().get()?.delete(toDelete) ?: false
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toReplace = resource as? R ?: return null
        return getOperation().get()?.withName(toReplace.metadata.name)?.replace(toReplace)
    }

    protected open fun getOperation(): Supplier<out ResourceOperation<R>?> {
        // default nop implementation
        return Supplier { null }
    }

}