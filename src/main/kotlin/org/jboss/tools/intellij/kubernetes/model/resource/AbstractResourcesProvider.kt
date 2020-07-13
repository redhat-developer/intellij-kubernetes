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
import org.jboss.tools.intellij.kubernetes.model.util.areEqual

abstract class AbstractResourcesProvider<R : HasMetadata> : IResourcesProvider<R> {

    protected val allResources = mutableSetOf<R>()

    override fun invalidate() {
        allResources.clear()
    }

    override fun invalidate(resource: HasMetadata) {
        allResources.remove(resource)
    }

    override fun add(resource: HasMetadata): Boolean {
        if (!kind.clazz.isAssignableFrom(resource::class.java)) {
            return false
        }
        return allResources.add(resource as R)
    }

    override fun remove(resource: HasMetadata): Boolean {
        val resourceClass = resource::class.java
        if (!kind.clazz.isAssignableFrom(resourceClass)) {
            return false
        }
        // do not remove by instance equality bcs instance to be removed can be different
        // ex. when removal is triggered by resource watch
        return allResources.removeIf { areEqual(it, resource) }
    }
}
