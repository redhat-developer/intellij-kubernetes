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
import com.redhat.devtools.intellij.kubernetes.model.util.isSameNamespace
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource

typealias NonNamespacedOperation<R> = NonNamespaceOperation<R, out KubernetesResourceList<R>, out Resource<R>>

interface INonNamespacedResourceOperator<R: HasMetadata, C: Client>: IResourceOperator<R>

abstract class NonNamespacedResourceOperator<R : HasMetadata, C : Client>(
    protected val client: C
) : AbstractResourceOperator<R>(), INonNamespacedResourceOperator<R, C> {

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
        logger<NamespacedResourceOperator<*, *>>().debug("Loading all $kind resources.")
        return getOperation()?.list()?.items ?: emptyList()
    }

    override fun watchAll(watcher: Watcher<in R>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        return getOperation()?.watch(typedWatcher)
    }

    override fun watch(resource: HasMetadata, watcher: Watcher<in R>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        return getOperation()?.withName(resource.metadata.name)?.watch(typedWatcher)
    }

    override fun delete(resources: List<HasMetadata>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val toDelete = resources as? List<R> ?: return false
        return getOperation()?.delete(toDelete) ?: false
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toReplace = resource as? R ?: return null
        removeResourceVersion(toReplace)
        removeUID(toReplace)
        return getOperation()?.withName(toReplace.metadata.name)?.replace(toReplace)
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toCreate = resource as? R ?: return null
        removeResourceVersion(toCreate)
        removeUID(toCreate)
        return getOperation()
            ?.withName(toCreate.metadata.name)
            ?.create(toCreate)
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toGet = resource as? R ?: return null
        return getOperation()?.withName(toGet.metadata.name)?.get()
    }

    protected open fun getOperation(): NonNamespacedOperation<R>? {
        // default nop implementation
        return null
    }

    /**
     * Returns the given resource only if it has the same namespace as the given spec. Returns null otherwise.
     * This may be required for non namespaced operators which handle namespaced resources in all namespaces (ex [AllPodsOperator])
     */
    protected fun ensureSameNamespace(spec: HasMetadata?, resource: HasMetadata?): HasMetadata? {
        return when {
            spec == null -> resource // no spec
            resource == null -> resource // null resource
            spec.metadata.namespace == null -> resource // namespace not specified
            spec.isSameNamespace(resource) -> resource // same namespace
            else -> null
        }
    }
}