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
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import java.util.function.Supplier

interface INonNamespacedResourcesProvider<R: HasMetadata, C: Client>: IResourcesProvider<R>

abstract class NonNamespacedResourcesProvider<R : HasMetadata, C : Client>(
    protected val client: C
) : AbstractResourcesProvider<R>(), INonNamespacedResourcesProvider<R, C> {

    override val allResources: MutableList<R> = mutableListOf()
        @Synchronized
        get() {
            if (field.isEmpty()) {
                field.addAll(loadAllResources())
            }
            return field
        }

    protected open fun loadAllResources(): List<R> {
        logger<NamespacedResourcesProvider<*, *>>().debug("Loading all $kind resources.")
        return getOperation().get()?.list()?.items ?: emptyList()
    }

    override fun getWatchable(): Supplier<Watchable<Watch, Watcher<R>>?> {
        return getOperation() as Supplier<Watchable<Watch, Watcher<R>>?>
    }

    protected open fun getOperation(): Supplier<WatchableAndListable<R>> {
        return Supplier { null }
    }

}
