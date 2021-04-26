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
package com.redhat.devtools.intellij.kubernetes.ui.editor

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.context.ActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.sameRevision
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import java.net.URL

class ResourceEditorModel(var resource: HasMetadata, context: ActiveContext<out HasMetadata, out KubernetesClient>) {

    private val operators = context.getAllResourceOperators(IResourceOperator::class.java)
    private val clients = context.clients
    val cluster: URL = clients.get().masterUrl
    private val watch = ResourceWatch()
    private val modelChange = ModelChangeObservable()

    fun isUpdatedOnCluster(): Boolean {
        val latestRevision = getLatestRevision() ?: return false
        return !resource.sameRevision(latestRevision)
    }

    fun isDeletedOnCluster(): Boolean {
        return getLatestRevision() == null
    }

    fun getLatestRevision(): HasMetadata? {
        val latest = clients.get().resource(resource).fromServer().get()
        if (latest != null) {
            resource = latest
        }
        return latest
    }

    fun replace(resource: HasMetadata) {
        val operator = getOperator(resource) ?: return
        operator.replace(resource)
    }

    fun watch() {
        logger<ActiveContext<*, *>>().debug("Watching ${resource.kind} ${ resource.metadata.name }.")
        val operator = getOperator(resource) ?: return
        watch.watch(
            resource,
            { watcher -> operator.watch(resource, watcher) },
            ResourceWatch.WatchListeners(
                { },
                { modelChange.fireRemoved(it) },
                { modelChange.fireModified(it) })
        )
    }

    fun stopWatch() {
        watch.stopWatch(resource)
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