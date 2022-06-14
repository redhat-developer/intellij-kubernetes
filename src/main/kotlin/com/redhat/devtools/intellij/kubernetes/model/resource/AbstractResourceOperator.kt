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
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException
import io.fabric8.kubernetes.client.dsl.LogWatch
import io.fabric8.kubernetes.client.dsl.Loggable
import io.fabric8.kubernetes.client.dsl.Waitable
import java.io.OutputStream
import java.util.concurrent.TimeUnit

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

    private fun isCorrectKind(resource: HasMetadata): Boolean {
        return kind.clazz.isAssignableFrom(resource::class.java)
    }

    protected fun watchLogWhenReady(operation: Waitable<R, *>, out: OutputStream): LogWatch? {
        try {
            operation.waitUntilReady(10, TimeUnit.SECONDS)
        } catch(e: KubernetesClientTimeoutException) {
            logger<AbstractResourceOperator<*, *>>().warn("Failed to wait resource to become ready.", e)
        }
        @Suppress("UNCHECKED_CAST")
        /**
         * [io.fabric8.kubernetes.client.dsl.Loggable] is added in the last subclass of [io.fabric8.kubernetes.client.dsl.Resource].
         * There's thus no generic superclass that also includes [Loggable.watchLog].
         */
        return (operation as? Loggable<LogWatch>)?.watchLog(out)
    }
}
