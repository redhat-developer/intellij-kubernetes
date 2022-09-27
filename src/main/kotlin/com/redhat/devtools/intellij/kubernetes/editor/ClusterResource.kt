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
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.ResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.WatchListeners
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isNotFound
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.isUnsupported
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException

/**
 * A resource that exists on the cluster. May be [pull], [set] etc.
 * Notifies listeners of addition, removal and modification if [watch]
 */
open class ClusterResource protected constructor(
    resource: HasMetadata,
    private val context: IActiveContext<out HasMetadata, out KubernetesClient>,
    private val watch: ResourceWatch<HasMetadata> = ResourceWatch(),
    private val modelChange: ResourceModelObservable = ResourceModelObservable()
) {
    companion object Factory {
        fun create(resource: HasMetadata?, context: IActiveContext<out HasMetadata, out KubernetesClient>?): ClusterResource? {
            return if (resource != null
                && context != null) {
                ClusterResource(resource, context)
            } else {
                logger<ResourceEditor>().warn("Could not create ClusterResource: no resource or context (resource = $resource, context = $context)")
                null
            }
        }
    }

    private val initialResource: HasMetadata = resource
    protected open var updatedResource: HasMetadata? = null
    protected open val watchListeners = WatchListeners(
        {},
        { removed ->
            set(null)
            setDeleted(true)
            modelChange.fireRemoved(removed)
        },
        { changed ->
            set(changed)
            setDeleted(false)
            modelChange.fireModified(changed)
        })
    private var isDeleted: Boolean = false
    private var closed: Boolean = false

    /**
     * Sets the given resource as the current value in this instance.
     * Nothing is done if the given resource is not the same resource (differs in name, namespace, kind).
     *
     * @param resource the resource that's the current resource value in this instance
     *
     * @see [pull]
     * @see [HasMetadata.isSameResource]
     */
    protected open fun set(resource: HasMetadata?) {
        synchronized(this) {
            if (resource != null
                && !initialResource.isSameResource(resource)) {
                return
            }
            this.updatedResource = resource
            setDeleted(false)
        }
    }

    /**
     * Returns the resource in the cluster. Returns the cached value by default,
     * requests it from cluster if instructed so by the given `forceRequest` parameter.
     *
     * @param forceRequest requests from server if set to true, returns the cached value otherwise
     *
     * @return the resource in the cluster
     * @throws ResourceException
     */
    fun pull(forceRequest: Boolean = false): HasMetadata? {
        synchronized(this) {
            if (forceRequest
                || updatedResource == null) {
                this.updatedResource = requestResource()
            }
            return updatedResource
        }
    }

    private fun requestResource(): HasMetadata? {
        return try {
            context.get(initialResource)
        } catch (e: RuntimeException) {
            val message =
                if (e is KubernetesClientException
                    && e.isUnsupported()
                ) {
                    // api discovery error
                    e.status.message
                } else {
                    "Could not retrieve ${initialResource.kind} ${initialResource.metadata?.name ?: ""}" +
                            " in version ${initialResource.apiVersion} from server"
                }
            throw ResourceException(message, e)
        }
    }

    protected open fun setDeleted(deleted: Boolean) {
        synchronized(this) {
            this.isDeleted = deleted
        }
    }

    fun isDeleted(): Boolean {
        synchronized(this) {
            return isDeleted
        }
    }

    fun isClosed(): Boolean {
        synchronized(this) {
            return this.closed
        }
    }

    fun canPush(toCompare: HasMetadata?): Boolean {
        if (toCompare == null) {
            return true
        }
        return try {
            val resource = pull()
            resource == null
                    || (isSameResource(toCompare) && isModified(toCompare))
        } catch (e: ResourceException) {
            logger<ClusterResource>().warn(
                "Could not request resource ${initialResource.kind} ${initialResource.metadata?.name ?: ""} from server ${context.masterUrl}",
                e)
            false
        }
    }

    /**
     * Pushes the given resource to the cluster. The currently existing resource on the cluster is replaced
     * if it is the same resource in an older version. A new resource is created if the given resource
     * doesn't exist on the cluster, it is replaced if it exists already.
     * Throws a [ResourceException] if the given resource is not the same as the resource initially given to this instance.
     *
     * @param resource the resource that shall be saved to the cluster
     */
    fun push(resource: HasMetadata): HasMetadata? {
        try {
            if (!initialResource.isSameResource(resource)) {
                throw ResourceException(
                    "Unsupported resource kind ${resource.kind} in version ${resource.apiVersion}."
                )
            }
            val updated = context.replace(resource)
            set(updated)
            return updated
        } catch (e: KubernetesClientException) {
            val details = getDetails(e)
            throw ResourceException(details, e)
        } catch (e: RuntimeException) {
            // ex. IllegalArgumentException
            throw ResourceException("Could not push ${resource.kind} ${resource.metadata.name ?: ""}", e)
        }
    }

    private fun getDetails(e: KubernetesClientException): String? {
        val message = e.message ?: return null
        val detailsIdentifier = "Message: "
        val detailsStart = message.indexOf(detailsIdentifier)
        if (detailsStart < 0) {
            return message
        }
        return message.substring(detailsStart + detailsIdentifier.length)
    }

    /**
     * Returns `true` if the given resource version is outdated when compared to the version of the resource on the cluster.
     * A given resourceVersion is considered outdated if it is not equal to the resourceVersion of the resource on the cluster.
     * Resource versions are specified as alphanumeric so no numeric comparison is possible. The docs state at
     * (Resource versions)[https://kubernetes.io/docs/reference/using-api/api-concepts/#resource-versions]:
     *
     * "You must not assume resource versions are numeric or collatable. API clients may only compare two resource
     * versions for equality (this means that you must not compare resource versions for greater-than or less-than
     * relationships)."
     *
     * @param resourceVersion the resource version to compare to the version of the cluster resource
     * @return true if the given resource version != resource version of the cluster resource
     *
     * @see io.fabric8.kubernetes.api.model.ObjectMeta.resourceVersion
     */
    fun isOutdated(resourceVersion: String?): Boolean {
        val resource = pull()
        val clusterVersion = resource?.metadata?.resourceVersion ?: return false
        return clusterVersion != resourceVersion
    }

    /**
     * Returns `true` if the given resource is the same as the resource in this cluster resource instance.
     * Returns `false` otherwise.
     *
     * @param toCompare the resource to compare to the initial cluster resource
     * @return true if the given resource is the same as the initial resource in this cluster resource
     *
     * @see [HasMetadata.isSameResource]
     */
    fun isSameResource(toCompare: HasMetadata?): Boolean {
        return initialResource.isSameResource(toCompare)
    }

    /**
     * Returns `true` if the given resource is not equal to the same resource on the cluster.
     * It returns `false` if the given resource doesn't exist on the cluster.
     * The cached cluster resource is used if it is present. It is retrieved from cluster if it isn't.
     *
     * @param toCompare resource to compare to the resource on the cluster
     */
    fun isModified(toCompare: HasMetadata?): Boolean {
        val resource = pull() ?: return false
        return resource != toCompare
    }

    /**
     * Returns `true` if the resource given to this instance exists on the cluster,
     *
     * @return true if the resource of this instance exists on the cluster
     */
    fun exists(): Boolean {
        return try {
            pull() != null
        } catch (e: ResourceException) {
            if (true == (e.cause as? KubernetesClientException)?.isNotFound()) {
                false
            } else {
                throw e
            }
        }
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
        try {
            logger<ClusterResource>().debug("Watching ${initialResource.kind} ${initialResource.metadata?.name ?: ""}.")
            forcedUpdate()
            watch.watch(
                initialResource,
                { watcher -> context.watch(initialResource, watcher) },
                watchListeners
            )
        } catch (e: KubernetesClientException) {
            val details = getDetails(e)
            throw ResourceException(details, e)
        } catch (e: java.lang.RuntimeException) {
            throw ResourceException(
                "Could not watch ${initialResource.kind} ${
                    initialResource.metadata?.name ?: ""
                }", e
            )
        }
    }

    private fun forcedUpdate() {
        val beforeUpdate = updatedResource ?: return
        val updated = pull(true)
        when {
            updated == null ->
                watchListeners.removed(beforeUpdate)
            updated != beforeUpdate ->
                watchListeners.replaced(updated)
        }
    }

    /**
     * Stops watching the resource given to this instance.
     */
    fun stopWatch() {
        // use the resource passed in initially, updated resource can have become null (deleted)
        logger<ClusterResource>().debug("Stopping watch for ${initialResource.kind} ${ initialResource.metadata?.name ?: "" }.")
        watch.stopWatch(initialResource)
    }

    /**
     * Closes this instance and stops the watches.
     */
    fun close() {
        synchronized(this) {
            if (closed) {
                return
            }
            this.closed = true
            watch.close()
        }
    }

    /**
     * Adds the given listener to the list of listeners that should be informed of changes to the resource given
     * to this instance.
     *
     * @param listener that should get called if a change (modification, deletion) was detected
     */
    fun addListener(listener: IResourceModelListener) {
        modelChange.addListener(listener)
    }
}