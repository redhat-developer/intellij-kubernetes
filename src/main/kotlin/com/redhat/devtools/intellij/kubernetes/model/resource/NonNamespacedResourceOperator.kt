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
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClient
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
        logger<NonNamespacedResourceOperator<*, *>>().debug("Loading all $kind resources.")
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

    override fun replace(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toReplace = resource as? R ?: return null
        return runWithoutServerSetProperties(toReplace) {
            client.adapt(KubernetesClient::class.java)
                .resource(toReplace)
                .patch()
        }
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toCreate = resource as? R ?: return null

        return runWithoutServerSetProperties(toCreate) {
            client.adapt(KubernetesClient::class.java)
                .resource(toCreate)
                .create()
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