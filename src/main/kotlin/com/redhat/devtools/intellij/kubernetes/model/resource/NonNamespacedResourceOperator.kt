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
import com.redhat.devtools.intellij.kubernetes.model.util.removeResourceVersion
import com.redhat.devtools.intellij.kubernetes.model.util.removeUid
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import java.io.OutputStream

typealias NonNamespacedOperation<R> = NonNamespaceOperation<R, out KubernetesResourceList<R>, out Resource<R>>

interface INonNamespacedResourceOperator<R: HasMetadata, C: Client>: IResourceOperator<R>

abstract class NonNamespacedResourceOperator<R : HasMetadata, C : Client>(
    client: C
) : AbstractResourceOperator<R, C>(client), INonNamespacedResourceOperator<R, C> {

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
        return getOperation()
            ?.list()?.items
            ?: emptyList()
    }

    override fun watchAll(watcher: Watcher<in R>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        return getOperation()
            ?.watch(typedWatcher)
    }

    override fun watch(resource: HasMetadata, watcher: Watcher<in R>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        return getOperation()
            ?.withName(resource.metadata.name)
            ?.watch(typedWatcher)
    }

    open fun watchLog(container: Container, resource: R, out: OutputStream): LogWatch? {
        val operation = getOperation()
            ?.withName(resource.metadata.name)
            ?: return null
        return watchLog(container, out, operation)
    }

    open fun watchExec(container: Container, resource: R, listener: ExecListener): ExecWatch? {
        val operation = getOperation()
            ?.withName(resource.metadata.name)
            ?: return null
        return watchExec(container, listener, operation)
    }

    override fun delete(resources: List<HasMetadata>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val toDelete = resources as? List<R> ?: return false
        return getOperation()
            ?.delete(toDelete)
            ?: false
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toReplace = resource as? R ?: return null
        val resourceVersion = removeResourceVersion(toReplace)
        val uid = removeUid(toReplace)
        val replaced = getOperation()
            ?.withName(toReplace.metadata.name)
            ?.replace(toReplace)
        // restore properties that were removed before sending
        toReplace.metadata.resourceVersion = resourceVersion
        toReplace.metadata.uid = uid
        return replaced
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toCreate = resource as? R ?: return null

        return runWithoutServerSetProperties(toCreate) {
            getOperation()
                ?.withName(toCreate.metadata.name)
                ?.create(toCreate)
        }
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toGet = resource as? R ?: return null
        return getOperation()
            ?.withName(toGet.metadata.name)
            ?.get()
    }

    protected open fun getOperation(): NonNamespacedOperation<R>? {
        // default nop implementation
        return null
    }

    /**
     * Returns the given resource only if it has the same namespace as the given spec. Returns null otherwise.
     * If no spec is given any resources is returned, regardless of its namespace.
     * This may be required for non namespaced operators which handle namespaced resources in all namespaces
     * (ex [com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator])
     *
     * @param spec the spec that prescribes the namespace
     * @param resource that should match the namespace prescribed by the spec
     *
     * @return the given resource if it's matching the namespace prescribed by the given spec
     */
    protected fun ensureSameNamespace(spec: HasMetadata?, resource: HasMetadata?): HasMetadata? {
        return when {
            spec == null -> resource // no spec
            resource == null -> null // null resource
            spec.metadata.namespace == null -> resource // namespace not specified
            spec.isSameNamespace(resource) -> resource // same namespace
            else -> null
        }
    }

}