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
import com.redhat.devtools.intellij.kubernetes.model.util.sameResource

abstract class AbstractResourcesProvider<R : HasMetadata> : IResourcesProvider<R> {

    protected val _allResources: MutableList<R> = mutableListOf()

    override fun invalidate() {
        logger<AbstractResourcesProvider<*>>().debug("Invalidating all $kind resources.")
        synchronized(_allResources) {
            _allResources.clear()
        }
    }

    override fun add(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourcesProvider<*>>().debug("Adding resource ${resource.metadata.name}.")
        // don't add resource if different instance of same resource is already contained
        synchronized(_allResources) {
            return when (val existing = _allResources.find { resource.sameResource(it) }) {
                null -> _allResources.add(resource as R)
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
        synchronized(_allResources) {
            // do not remove by instance equality (ex. when removal is triggered by resource watch)
            // or equals bcs instance to be removed can be different and not equals either
            // (#equals would not match bcs properties - ex. phase - changed)
            return _allResources.removeIf { resource.sameResource(it) }
        }
    }

    override fun replace(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourcesProvider<*>>().debug("Replacing resource ${resource.metadata.name}.")
        synchronized(_allResources) {
            val toReplace = _allResources.find { resource.sameResource(it) } ?: return false
            return replace(toReplace, resource)
        }
    }

    private fun replace(toReplace: R, replaceBy: HasMetadata): Boolean {
        val indexOf = _allResources.indexOf(toReplace)
        if (indexOf < 0) {
            return false
        }
        _allResources[indexOf] = replaceBy as R
        return true
    }

    private fun isCorrectKind(resource: HasMetadata): Boolean {
        return kind.clazz.isAssignableFrom(resource::class.java)
    }
}
