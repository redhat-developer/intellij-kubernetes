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
import io.fabric8.kubernetes.client.dsl.Listable

interface INonNamespacedResourcesProvider<T: HasMetadata>: IResourcesProvider<T>

abstract class NonNamespacedResourcesProvider<R : HasMetadata, C: KubernetesClient>(protected val client: C)
    : AbstractResourcesProvider<R>(), INonNamespacedResourcesProvider<R> {

    override fun getAllResources(): Collection<R> {
        if (allResources.isEmpty()) {
            allResources.addAll(loadAllResources())
        }
        return allResources
    }

    protected open fun loadAllResources(): List<R> {
        return (getWatchable().invoke() as? Listable<KubernetesResourceList<R>>)?.list()?.items ?: emptyList()
    }
}
