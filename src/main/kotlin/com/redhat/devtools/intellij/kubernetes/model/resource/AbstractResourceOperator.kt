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
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import io.fabric8.kubernetes.client.Client

abstract class AbstractResourceOperator<R : HasMetadata, C : Client>(protected val client: C) : IResourceOperator<R> {

    protected val _allResources: MutableList<R> = mutableListOf()

    override fun invalidate() {
        logger<AbstractResourceOperator<*, *>>().debug("Invalidating all $kind resources.")
        synchronized(_allResources) {
            _allResources.clear()
        }
    }

    override fun added(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourceOperator<*, *>>().debug("Adding resource ${resource.metadata.name}.")
        // don't add resource if different instance of same resource is already contained
        synchronized(_allResources) {
            @Suppress("UNCHECKED_CAST")
            return when (val existing = _allResources.find { resource.isSameResource(it) }) {
                null -> _allResources.add(resource as R)
                resource -> false
                else -> replace(existing, resource)
            }
        }
    }

    override fun removed(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourceOperator<*, *>>().debug("Removing resource ${resource.metadata.name}.")
        synchronized(_allResources) {
            // do not remove by instance equality (ex. when removal is triggered by resource watch)
            // or equals bcs instance to be removed can be different and not equals either
            // (#equals would not match bcs properties - ex. phase - changed)
            return _allResources.removeIf { resource.isSameResource(it) }
        }
    }

    override fun replaced(resource: HasMetadata): Boolean {
        if (!isCorrectKind(resource)) {
            return false
        }
        logger<AbstractResourceOperator<*, *>>().debug("Replacing resource ${resource.metadata.name}.")
        synchronized(_allResources) {
            val toReplace = _allResources.find { resource.isSameResource(it) } ?: return false
            return replace(toReplace, resource)
        }
    }

    private fun replace(toReplace: R, replaceBy: HasMetadata): Boolean {
        val indexOf = _allResources.indexOf(toReplace)
        if (indexOf < 0) {
            return false
        }
        @Suppress("UNCHECKED_CAST")
        _allResources[indexOf] = replaceBy as R
        return true
    }

    protected fun runWithoutServerSetProperties(resource: R, operation: () -> R?): R? {
        // remove properties
        val resourceVersion = removeResourceVersion(resource)
        val uid = removeUid(resource)
        val value = operation.invoke()
        // restore properties
        setResourceVersion(resourceVersion, resource)
        setUid(uid, resource)
        return value
    }

    protected fun removeResourceVersion(toCreate: R): String? {
        val value = toCreate.metadata.resourceVersion
        toCreate.metadata.resourceVersion = null
        return value
    }

    protected fun setResourceVersion(resourceVersion: String?, toCreate: R) {
        toCreate.metadata.resourceVersion = resourceVersion
    }

    protected fun removeUid(toCreate: R): String? {
        val value = toCreate.metadata.uid
        toCreate.metadata.uid = null
        return value
    }

    protected fun setUid(uid: String?, toCreate: R) {
        toCreate.metadata.uid = uid
    }

    private fun isCorrectKind(resource: HasMetadata): Boolean {
        return kind.clazz.isAssignableFrom(resource::class.java)
    }
}
