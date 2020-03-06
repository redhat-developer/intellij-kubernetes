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
import io.fabric8.kubernetes.client.KubernetesClient

abstract class ResourcesProvider<R : HasMetadata, C : KubernetesClient>(
    protected val client: C
) : IResourcesProvider<R> {

    protected abstract val allResources: MutableSet<R>

    override fun getAllResources(): Collection<R> {
        return allResources
    }


    override fun hasResource(resource: R): Boolean {
        return allResources.contains(resource)
    }

    override fun invalidate() {
        allResources.clear()
    }

    override fun invalidate(resource: HasMetadata) {
        allResources.remove(resource)
    }

    override fun add(resource: HasMetadata): Boolean {
        if (!kind.isAssignableFrom(resource::class.java)) {
            return false
        }
        return allResources.add(resource as R)
    }

    override fun remove(resource: HasMetadata): Boolean {
        if (!kind.isAssignableFrom(resource::class.java)) {
            return false
        }
        return allResources.remove(resource)
    }
}
