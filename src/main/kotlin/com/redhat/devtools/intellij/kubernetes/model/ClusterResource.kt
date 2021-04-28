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
import com.redhat.devtools.intellij.kubernetes.model.context.ActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

class ClusterResource(resource: HasMetadata, context: ActiveContext<out HasMetadata, out KubernetesClient>) {

    private var resource: HasMetadata = resource
    val contextName: String = context.context.name
    private val operators = context.getAllResourceOperators(IResourceOperator::class.java)
    private val client = context.clients.get()
    private val watch = ResourceWatch<HasMetadata>()
    private val modelChange = ModelChangeObservable()

    fun get(): HasMetadata {
        synchronized(this) {
            return resource
        }
    }

    fun set(resource: HasMetadata) {
        synchronized(this) {
            this.resource = resource
        }
    }

    fun isUpdated(): Boolean {
        val latestRevision = getLatest() ?: return false
        return !get().sameRevision(latestRevision)
    }

    fun isDeleted(): Boolean {
        return getLatest() == null
    }

    fun getLatest(): HasMetadata? {
        return client.resource(get()).fromServer().get()
    }

    fun replace(resource: HasMetadata): HasMetadata? {
        val operator = getOperator(resource) ?: return null
        return operator.replace(resource)
    }

    fun watch() {
        logger<ClusterResource>().debug("Watching ${get().kind} ${ get().metadata.name }.")
        val operator = getOperator(get()) ?: return
        watch.watch(
            get(),
            { watcher -> operator.watch(get(), watcher) },
            ResourceWatch.WatchListeners(
                {},
                {
                    modelChange.fireRemoved(it)
                },
                {
                    set(it)
                    modelChange.fireModified(it)
                })
        )
    }

    fun stopWatch() {
        logger<ClusterResource>().debug("Stopping watch for ${resource.kind} ${ resource.metadata.name }.")
        watch.stopWatch(get())
    }

    fun close() {
        watch.close()
    }


    fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        modelChange.addListener(listener)
    }

    private fun getOperator(resource: HasMetadata): IResourceOperator<*>? {
        val kind = ResourceKind.create(resource)
        @Suppress("UNCHECKED_CAST")
        return operators[kind]
    }
}