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

    private val originaryResource: HasMetadata? = resource
    private var updatedResource: HasMetadata? = resource
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
                if (originaryResource != null) {
                    this.updatedResource = operator?.get(this.originaryResource)
                }
            }
            return updatedResource
        }
    }

    fun saveToCluster(resource: HasMetadata): HasMetadata? {
        return if (updatedResource != null) {
            operator?.replace(resource)
        } else {
            operator?.create(resource)
        }
    }

    fun set(resource: HasMetadata?) {
        synchronized(this) {
            this.updatedResource = resource
        }
    }

    fun isOutdated(compared: HasMetadata?): Boolean {
        val resource = get(true)
        if (compared == null
            || resource == null) {
            return false
        }
        return !resource.sameRevision(compared)
    }

    fun isDeleted(): Boolean {
        return get(true) == null
    }

    fun watch() {
        if (operator != null
            // use the resource passed in initially, updated resource can have become null (deleted)
            && originaryResource != null) {
            logger<ClusterResource>().debug("Watching ${originaryResource.kind} ${originaryResource.metadata.name}.")
            watch.watch(
                originaryResource,
                { watcher -> operator?.watch(originaryResource, watcher) },
                ResourceWatch.WatchListeners(
                    {},
                    { removed ->
                        set(null)
                        modelChange.fireRemoved(removed)
                    },
                    { changed ->
                        set(changed)
                        modelChange.fireModified(changed)
                    })
            )
        }
    }

    fun stopWatch() {
        // use the resource passed in initially, updated resource can have become null (deleted)
        val resource = originaryResource ?: return
        logger<ClusterResource>().debug("Stopping watch for ${resource.kind} ${resource.metadata.name}.")
        watch.stopWatch(resource)
    }

    fun close() {
        watch.close()
    }

    fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        modelChange.addListener(listener)
    }
}