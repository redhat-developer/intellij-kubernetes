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
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.Client
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.Resource

typealias NamespacedOperation<R> = MixedOperation<R, out KubernetesResourceList<R>, out Resource<R>>

interface INamespacedResourceOperator<R: HasMetadata, C: Client>: IResourceOperator<R> {
    var namespace: String?
}

abstract class NamespacedResourceOperator<R : HasMetadata, C: Client>(
    protected val client: C,
    namespace: String? = client.namespace
) : AbstractResourceOperator<R>(), INamespacedResourceOperator<R, C> {

    final override var namespace: String? = namespace
        set(namespace) {
            logger<NamespacedResourceOperator<*, *>>().debug("Using new namespace $namespace.")
            invalidate()
            field = namespace
        }

    override val allResources: List<R>
        get() {
            synchronized(_allResources) {
                if (_allResources.isEmpty()) {
                    val namespace = this.namespace
                    if (namespace != null) {
                        _allResources.addAll(loadAllResources(namespace))
                    } else {
                        logger<NamespacedResourceOperator<*, *>>().debug("Could not load $kind resources: no namespace set.")
                    }
                }
                return _allResources
            }
        }


    protected open fun loadAllResources(namespace: String): List<R> {
        logger<NamespacedResourceOperator<*, *>>().debug("Loading $kind resources in namespace $namespace.")
        return getOperation()?.inNamespace(namespace)?.list()?.items ?: emptyList()
    }

    override fun watchAll(watcher: Watcher<in R>): Watch? {
        val inNamespace = namespace
        if (inNamespace == null) {
            logger<NamespacedResourceOperator<*, *>>().debug("Returned empty watch for $kind: no namespace set.")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        return getOperation()
            ?.inNamespace(inNamespace)
            ?.watch(typedWatcher)
    }

    override fun watch(resource: HasMetadata, watcher: Watcher<in R>): Watch? {
        @Suppress("UNCHECKED_CAST")
        val typedWatcher = watcher as? Watcher<R> ?: return null
        val inNamespace = resourceOrCurrentNamespace(resource)
        return getOperation()
            ?.inNamespace(inNamespace)
            ?.withName(resource.metadata.name)
            ?.watch(typedWatcher)
    }

    override fun delete(resources: List<HasMetadata>): Boolean {
        if (namespace == null) {
            return false
        }
        @Suppress("UNCHECKED_CAST")
        val toDelete = resources as? List<R> ?: return false
        return getOperation()?.delete(toDelete) ?: false
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toReplace = resource as? R ?: return null
        removeResourceVersion(toReplace)
        removeUID(toReplace)
        val inNamespace = resourceOrCurrentNamespace(toReplace)
        return getOperation()
            ?.inNamespace(inNamespace)
            ?.withName(toReplace.metadata.name)
            ?.replace(toReplace)
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toCreate = resource as? R ?: return null
        removeResourceVersion(toCreate)
        removeUID(toCreate)
        val inNamespace = resourceOrCurrentNamespace(toCreate)
        return getOperation()
            ?.inNamespace(inNamespace)
            ?.withName(toCreate.metadata.name)
            ?.create(toCreate)
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        @Suppress("UNCHECKED_CAST")
        val toGet = resource as? R ?: return null
        val inNamespace = resourceOrCurrentNamespace(toGet)
        return getOperation()
            ?.inNamespace(inNamespace)
            ?.withName(toGet.metadata.name)
            ?.get()
    }

    protected fun resourceOrCurrentNamespace(resource: HasMetadata): String {
        return resource.metadata.namespace ?: this.namespace
        ?: throw KubernetesClientException("No namespace found, neither in operated resource ${resource.metadata.name} nor in current namespace.")
    }

    protected open fun getOperation(): NamespacedOperation<R>? {
        return null
    }
}
