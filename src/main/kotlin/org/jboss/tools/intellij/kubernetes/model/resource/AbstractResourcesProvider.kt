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
import org.jboss.tools.intellij.kubernetes.model.util.sameUid

abstract class AbstractResourcesProvider<R : HasMetadata> : IResourcesProvider<R> {

    protected val allResources = mutableListOf<R>()

    override fun invalidate() {
        logger<AbstractResourcesProvider<*>>().debug("Invalidating all $kind resources.")
        synchronized(allResources) {
            allResources.clear()
        }
    }

    override fun add(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourcesProvider<*>>().debug("Adding resource ${resource.metadata.name}.")
        // don't add resource if different instance of same resource is already contained
        synchronized(allResources) {
            return when (val existing = allResources.find { resource.sameUid(it) }) {
                null -> allResources.add(resource as R)
                resource -> false
                else -> replace(existing, resource)
            }
        }
    }

    override fun remove(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourcesProvider<*>>().debug("Removing resource ${resource.metadata.name}.")
        synchronized(allResources) {
            // do not remove by instance equality (ex. when removal is triggered by resource watch)
            // or equals bcs instance to be removed can be different and not equals either
            // (#equals would not match bcs properties - ex. phase - changed)
            return allResources.removeIf { resource.sameUid(it) }
        }
    }

    override fun replace(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourcesProvider<*>>().debug("Replacing resource ${resource.metadata.name}.")
        synchronized(allResources) {
            val toReplace = allResources.find { resource.sameUid(it) } ?: return false
            return replace(toReplace, resource)
        }
    }

    private fun replace(toReplace: R, replaceBy: HasMetadata): Boolean {
        val indexOf = allResources.indexOf(toReplace)
        if (indexOf < 0) {
            return false
        }
        allResources[indexOf] = replaceBy as R
        return true
    }

    private fun isCorrectKind(resource: HasMetadata): Boolean {
        return kind.clazz.isAssignableFrom(resource::class.java)
    }
}
