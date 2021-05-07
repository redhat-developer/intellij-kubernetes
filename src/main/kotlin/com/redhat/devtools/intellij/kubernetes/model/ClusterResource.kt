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
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceOperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.util.*
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException

/**
 * A resource that exists on the cluster. May be [get], [set] etc.
 * Notifies listeners of addition, removal and modification if [watch]
 */
class ClusterResource(resource: HasMetadata, val contextName: String) {

    private val initialResource: HasMetadata = resource
    private var updatedResource: HasMetadata? = resource
    private val clients: Clients<out KubernetesClient> = ::createClients.invoke(contextName)
    private val operator: IResourceOperator<out HasMetadata>? by lazy {
        createOperator(resource)
    }

    private val watch = ResourceWatch<HasMetadata>()
    private val modelChange = ModelChangeObservable()

    fun get(forceLatest: Boolean = false): HasMetadata? {
        synchronized(this) {
            if (forceLatest) {
                try {
                    this.updatedResource = operator?.get(this.initialResource)
                } catch (e: KubernetesClientException) {
                    if (e.isNotFound()) {
                        this.updatedResource = null
                    } else {
                        throw e
                    }
                }
            }
            return updatedResource
        }
    }

    fun saveToCluster(resource: HasMetadata): HasMetadata? {
        return if (updatedResource != null
            && updatedResource!!.sameResource(resource)) {
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
        return resource.sameResource(compared)
                && resource.newerRevision(compared)
    }

    fun isSameResource(compared: HasMetadata?): Boolean {
        if (compared == null) {
            return false
        }
        return initialResource.sameResource(compared)
    }

    fun isDeleted(): Boolean {
        return get(true) == null
    }

    fun watch() {
        if (operator != null) {
            logger<ClusterResource>().debug("Watching ${initialResource.kind} ${initialResource.metadata.name}.")
            watch.watch(
                initialResource,
                { watcher -> operator?.watch(initialResource, watcher) },
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
        logger<ClusterResource>().debug("Stopping watch for ${initialResource.kind} ${initialResource.metadata.name}.")
        watch.stopWatch(initialResource)
    }

    fun close() {
        watch.close()
    }

    fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        modelChange.addListener(listener)
    }

    private fun createOperator(resource: HasMetadata): IResourceOperator<out HasMetadata>? {
        return if (resource is GenericCustomResource) {
            val definitions = clients.get().apiextensions().v1beta1().customResourceDefinitions().list().items
            CustomResourceOperatorFactory.create(resource, definitions, clients.get())
        } else {
            val kind = ResourceKind.create(resource)
            OperatorFactory.create(kind, clients)
        }
    }

}