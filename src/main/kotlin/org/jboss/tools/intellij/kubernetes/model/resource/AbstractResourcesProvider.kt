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

    protected val allResources = mutableListOf<R>()

    override fun invalidate() {
        synchronized(allResources) {
            allResources.clear()
        }
    }

    override fun add(resource: HasMetadata): Boolean {
        if (!isOfCorrectKind(resource)) {
            return false
        }
        // don't add resource if different instance of same resource is already contained
        synchronized(allResources) {
            return allResources.find { areEqual(it, resource) } == null
                    && allResources.add(resource as R)
        }
    }

    override fun remove(resource: HasMetadata): Boolean {
        if (!isOfCorrectKind(resource)) {
            return false
        }
        // do not remove by instance equality bcs instance to be removed can be different
        // ex. when removal is triggered by resource watch
        synchronized(allResources) {
            return allResources.removeIf { areEqual(it, resource) }
        }
    }

    override fun replace(resource: HasMetadata): Boolean {
        if (!isOfCorrectKind(resource)) {
            return false
        }
        synchronized(allResources) {
            val toReplace = allResources.find { areEqual(it, resource) } ?: return false
            val indexOf = allResources.indexOf(toReplace)
            if (indexOf < 0) {
                return false
            }
            allResources[indexOf] = resource as R
            return true
        }
    }

    private fun isOfCorrectKind(resource: HasMetadata): Boolean {
        return kind.clazz.isAssignableFrom(resource::class.java)
    }
}
