/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceOperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import com.redhat.devtools.intellij.kubernetes.model.util.createClients
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

/**
 * A resource that exists on the cluster. May be [get], [set] etc.
 * Notifies listeners of addition, removal and modification if [watch]
 */
class ClusterResource(resource: HasMetadata, val contextName: String) {

    private var resource: HasMetadata? = resource
    private val clients: Clients<out KubernetesClient> = ::createClients.invoke(contextName)
    private val operator by lazy {
        if (resource is GenericCustomResource) {
            val definitions = clients.get().apiextensions().v1beta1().customResourceDefinitions().list().items
            CustomResourceOperatorFactory.create(resource, definitions, clients.get())
        } else {
            val kind = ResourceKind.create(resource)
            OperatorFactory.create(kind, clients)
        }
    }
    private val watch = ResourceWatch<HasMetadata>()
    private val modelChange = ModelChangeObservable()

    fun get(forceLatest: Boolean = false): HasMetadata? {
        synchronized(this) {
            if (forceLatest) {
                val updated = getLatest()
                if (updated != null) {
                    this.resource = updated
                }
            }
            return resource
        }
    }

    private fun getLatest(): HasMetadata? {
        val resource = get() ?: return null
        return operator?.get(resource)
    }

    fun saveToCluster(resource: HasMetadata): HasMetadata? {
        return operator?.replace(resource)
    }

    fun set(resource: HasMetadata?) {
        synchronized(this) {
            this.resource = resource
        }
    }

    fun isOutdated(compared: HasMetadata?): Boolean {
        val resource = get()
        if (compared == null
            || resource == null) {
            return false
        }
        return !resource.sameRevision(compared)
    }

    fun isDeleted(): Boolean {
        return get() == null
    }

    fun watch() {
        val resource = get()
        if (operator != null
            && resource != null) {
            logger<ClusterResource>().debug("Watching ${resource.kind} ${resource.metadata.name}.")
            watch.watch(
                resource,
                { watcher -> operator?.watch(resource, watcher) },
                ResourceWatch.WatchListeners(
                    {},
                    { resource ->
                        set(null)
                        modelChange.fireRemoved(resource)
                    },
                    { resource ->
                        set(resource)
                        modelChange.fireModified(resource)
                    })
            )
        }
    }

    fun stopWatch() {
        val resource = get()
        if (resource != null) {
            logger<ClusterResource>().debug("Stopping watch for ${resource.kind} ${resource.metadata.name}.")
            watch.stopWatch(resource)
        }
    }

    fun close() {
        watch.close()
    }

    fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        modelChange.addListener(listener)
    }
}