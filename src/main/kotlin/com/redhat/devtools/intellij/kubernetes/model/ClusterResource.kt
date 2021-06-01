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
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.WatchListeners
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
open class ClusterResource(
    resource: HasMetadata,
    private val contextName: String,
    private val clients: Clients<out KubernetesClient> = ::createClients.invoke(contextName),
    private val watch: ResourceWatch<HasMetadata> = ResourceWatch(),
    private val modelChange: ModelChangeObservable = ModelChangeObservable()
) {
    private val initialResource: HasMetadata = resource
    private var updatedResource: HasMetadata? = null
    private val operator: IResourceOperator<out HasMetadata>? by lazy {
        createOperator(resource)
    }
    protected open val watchListeners = WatchListeners(
        {},
        { removed ->
            set(null)
            setDeleted(true)
            modelChange.fireRemoved(removed)
        },
        { changed ->
            set(changed)
            modelChange.fireModified(changed)
        })
    private var isDeleted: Boolean = false

    /**
     * Sets the given resource as the current value in this instance.
     * Nothing is done if the given resource is not the same resource (differs in name, namespace, kind).
     *
     * @param resource the resource that's the current resource value in this instance
     *
     * @see [get]
     * @see [HasMetadata.isSameResource]
     */
    fun set(resource: HasMetadata?) {
        synchronized(this) {
            if (resource != null
                && !initialResource.isSameResource(resource)) {
                return
            }
            this.updatedResource = resource
        }
    }

    /**
     * Returns the resource in the cluster. Returns the cached value by default,
     * requests it from cluster if instructed so by the given `forceRequest` parameter.
     *
     * @param forceRequest requests from server if set to true, returns the cached value otherwise
     *
     * @return the resource in the cluster
     */
    fun get(forceRequest: Boolean = false): HasMetadata? {
        synchronized(this) {
            if (forceRequest
                || updatedResource == null) {
                this.updatedResource = request()
            }
            return updatedResource
        }
    }

    private fun request(): HasMetadata? {
        return try {
            if (operator == null) {
                throw ResourceException(
                    "Unsupported resource kind ${initialResource.kind} in version ${initialResource.apiVersion}."
                )
            }
            operator!!.get(this.initialResource)
        } catch (e: KubernetesClientException) {
            if (e.isNotFound()) {
                null
            } else {
                throw ResourceException(e.cause?.message, e)
            }
        }
    }

    private fun setDeleted(deleted: Boolean) {
        synchronized(this) {
            this.isDeleted = deleted
        }
    }

    fun isDeleted(): Boolean {
        synchronized(this) {
            return isDeleted
        }
    }

    /**
     * Pushes the given resource to the cluster. The currently existing resource on the cluster is replaced
     * if it is the same resource in an older version. A new resource is created if the given resource
     * doesn't exist on the cluster, it is replaced if it exists already.
     * Throws a [ResourceException] if the given resource is not the same as the resource initially given to this instance.
     *
     * @param resource the resource that shall be save to the cluster
     */
    fun push(resource: HasMetadata): HasMetadata? {
        if (operator == null
            || !initialResource.isSameResource(resource)) {
            throw ResourceException(
                "unsupported resource kind ${resource.kind} in version ${resource.apiVersion}.")
        }
        try {
            this.updatedResource =
                if (exists()) {
                    operator?.replace(resource)
                } else {
                    operator?.create(resource)
                }
            return updatedResource
        } catch (e: KubernetesClientException) {
            val details = getDetails(e)
            throw ResourceException(details, e)
        }
    }

    private fun getDetails(e: KubernetesClientException): String? {
        if (e.message == null) {
            return null
        }

        val detailsIdentifier = "Message: "
        val detailsStart = e.message!!.indexOf(detailsIdentifier)
        if (detailsStart < 0) {
            return e.message
        }
        return e.message!!.substring(detailsStart + detailsIdentifier.length)
    }

    /**
     * Returns `true` if the given resource is outdated compared to the latest resource on the cluster.
     * A resource is considered outdated if it is the same resource and is an older version of the (latest) resource in the cluster.
     * The latest resource form cluster is retrieved to make sure the most accurate response is given.
     * Returns `true` if the given resource is `null` and the resource on the cluster isn't.
     * Returns `false` if the given resource is not `null` but the resource on the cluster is `null`.
     *
     * @param toCompare the resource to compare to the latest cluster resource
     * @return true if the given resource is outdated compared to the latest cluster resource
     *
     * @see HasMetadata.isSameResource
     * @see HasMetadata.isNewerVersionThan
     */
    fun isOutdated(toCompare: HasMetadata?): Boolean {
        val resource = get(true)
        return if (toCompare == null) {
            resource != null
        } else {
            if (resource == null) {
                return false
            }
            return resource.isNewerVersionThan(toCompare)
        }
    }

    private fun areEqual(thisResource: HasMetadata?, thatResource: HasMetadata?): Boolean {
        return (thisResource == null && thatResource == null)
                || thisResource == thatResource
    }

    /**
     * Returns `true` if the given resource is the same as the resource that was given when creating this cluster resource instance.
     * A resource is considered the same if it is equal in [io.fabric8.kubernetes.api.model.ObjectMeta.getUid]
     * and [io.fabric8.kubernetes.api.model.ObjectMeta.getSelfLink]
     * Returns `false` otherwise
     *
     * @param toCompare the resource to compare to the initial cluster resource
     * @return true if the given resource is the same as the initial resource in this cluster resource
     */
    fun isSameResource(toCompare: HasMetadata?): Boolean {
        try {
            val resource = get(false)
            if (toCompare == null) {
                return resource != null
            }

            return toCompare.isSameResource(resource)
        } catch (e: ResourceException) {
            return false
        }
    }

    fun canPush(toCompare: HasMetadata?): Boolean {
        val resource = get(false)
        if (toCompare == null) {
            return true
        }
        return if (resource == null) {
            true
        } else {
            isModified(toCompare)
        }
    }

    /**
     * Returns `true` if the given resource is of the same kind and
     * modified compared to the same resource on the cluster.
     *
     * @param toCompare resource to compare to the resource on the cluster
     */
    fun isModified(toCompare: HasMetadata?): Boolean {
        return isSameResource(toCompare)
                && !areEqual(updatedResource, toCompare)
    }

    /**
     * Returns `true` if the resource given to this instance exists on the cluster,
     *
     * @return true if the resource of this instance exists on the cluster
     */
    fun exists(): Boolean {
        return get(false) != null
    }

    /**
     * Starts watching the resource given to this instance. Modification & deletions to the resource are tracked
     * and notified to listeners of this instance. Calling [watch] several times won't watch multiple times.
     * Watching may be stopped by calling [stopWatch]
     *
     * @see [addListener]
     * @see [stopWatch]
     */
    fun watch() {
        if (operator == null) {
            logger<ClusterResource>().debug(
                "Cannot watch ${initialResource.kind} ${initialResource.metadata.name}. No operator present")
            return
        }
        logger<ClusterResource>().debug("Watching ${initialResource.kind} ${initialResource.metadata.name}.")
        watch.watch(
            initialResource,
            { watcher -> operator?.watch(initialResource, watcher) },
            watchListeners
        )
    }

    /**
     * Stops watching the resource given to this instance.
     */
    fun stopWatch() {
        // use the resource passed in initially, updated resource can have become null (deleted)
        logger<ClusterResource>().debug("Stopping watch for ${initialResource.kind} ${initialResource.metadata.name}.")
        watch.stopWatch(initialResource)
    }

    /**
     * Closes this instance and stops the watches.
     */
    fun close() {
        watch.close()
    }

    /**
     * Adds the given listener to the list of listeners that should be informed of changes to the resource given
     * to this instance.
     *
     * @param listener that should get called if a change (modification, deletion) was detected
     */
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        modelChange.addListener(listener)
    }

    protected open fun createOperator(resource: HasMetadata): IResourceOperator<out HasMetadata>? {
        return if (resource is GenericCustomResource) {
            val client = clients.get()
            val definitions = CustomResourceOperatorFactory.getDefinitions(client)
            CustomResourceOperatorFactory.create(resource, definitions, client)
        } else {
            val kind = ResourceKind.create(resource)
            OperatorFactory.create(kind, clients)
        }
    }
}