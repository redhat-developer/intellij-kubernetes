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
package com.redhat.devtools.intellij.kubernetes.model.context

import com.redhat.devtools.intellij.common.kubernetes.ClusterInfo
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.OSClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.Replicator
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.OpenShiftClient
import java.net.URL

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

    companion object Factory {
        fun createLazyOpenShift(
            client: ClientAdapter<out KubernetesClient>,
            modelChange: IResourceModelObservable
        ): IActiveContext<out HasMetadata, out KubernetesClient>? {
            val currentContext = client.config.currentContext ?: return null
            return LazyOpenShiftContext(currentContext, modelChange, client as KubeClientAdapter)
        }

        fun createOpenShift(
            client: OSClientAdapter,
            modelChange: IResourceModelObservable
        ): IActiveContext<Project, OpenShiftClient>? {
            val currentContext = client.config.currentContext ?: return null
            return OpenShiftContext(currentContext, modelChange, client)
        }

    }

    /**
     * The scope in which resources may exist.
     */
    enum class ResourcesIn {
        CURRENT_NAMESPACE, ANY_NAMESPACE, NO_NAMESPACE;

        companion object {
            fun valueOf(resource: HasMetadata, currentNamespace: String?): ResourcesIn {
                return when (resource.metadata.namespace) {
                    currentNamespace -> CURRENT_NAMESPACE
                    else -> ANY_NAMESPACE
                }
            }
        }
    }

    val namespaceKind : ResourceKind<out HasMetadata>

    /**
     * The master url for this context. This is the url of the cluster for this context.
     */
    val masterUrl: URL

    /**
     * The version of the cluster for this context
     */
    val version: ClusterInfo

    /**
     * Returns the list of internal [IResourceOperator]s that are available in this context.
     * [IResourceOperator]s that are contributed by registrations to the extension point are not included.
     *
     * @return the list of [IResourceOperator]s that are available in this context.
     * @see IResourceOperator
     * @see com.redhat.devtools.intellij.kubernetes.model.context.ActiveContext.getExtensionResourceOperators
     */
    fun getInternalResourceOperators(): List<IResourceOperator<out HasMetadata>>

    /**
     * Returns {@code true} if this context is an OpenShift context. This is true for context with an OpenShift cluster.
     */
    fun isOpenShift(): Boolean

    /**
     * Returns the current namespace for this context or {@code null} if there's none.
     */
    fun getCurrentNamespace(): String?

    /**
     * Returns `true` if the given resource is the current namespace.
     * Returns `false` otherwise.
     */
    fun isCurrentNamespace(resource: HasMetadata): Boolean

    /**
     * Returns `true` if the given name is the name of the current namespace.
     * Returns `false` otherwise.
     */
    fun isCurrentNamespace(namespace: String): Boolean

    /**
     * Deletes the given resources.
     *
     * @param resources the resources to delete
     * @param force whether deletion should be forced (immediate deletion, no grace period)
     */
    fun delete(resources: List<HasMetadata>, force: Boolean)

    /**
     * Returns all resources of the given kind in the given scope.
     *
     * @param kind the kind of resources that shall be returned
     * @param resourcesIn the scope where to look for the requested resources
     * @return all resources of the requested kind
     *
     * @see ResourceKind
     * @see ResourcesIn
     */
    fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R>

    /**
     * Returns all resources of the kind specified by the given custom resource definition.
     *
     * @param definition the definition that specifies the kind of custom resources
     * @return all resources of the requested kind
     *
     * @see [io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition]
     * @see [io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec]
     */
    fun getAllResources(definition: CustomResourceDefinition): Collection<GenericKubernetesResource>

    /**
     * Returns the latest version of the given resource from the cluster. Returns `null` if none was found.
     *
     * @param resource which is to be requested from cluster
     *
     * @return resource that was retrieved from cluster
     */
    fun get(resource: HasMetadata): HasMetadata?

    /**
     * Creates the given resource on the cluster if it doesn't exist. Throws if it exists already.
     *
     * @param resource that shall be created on the cluster
     *
     * @return the resource that was created
     */
    fun create(resource: HasMetadata): HasMetadata?

    /**
     * Replaces the given resource on the cluster if it exists. Throws if it doesn't.
     *
     * @param resource that shall be replaced on the cluster
     *
     * @return the resource that was replaced
     */
    fun replace(resource: HasMetadata): HasMetadata?

    /**
     * Sets the replicas for the given [Replicator]
     *
     * @param replicas the number of replicas to set
     * @param replicator the resource to set the replicas to
     *
     * @see Replicator
     */
    fun setReplicas(replicas: Int, replicator: Replicator)

    /**
     * Returns the replicas for the given resource.
     * Returns `null` replicas are not defined or the given resource does not specify those.
     *
     * @param resource the resource to get the replicas from
     * @return returns an instance of the resource that can be replicated or null if there's none
     */
    fun getReplicas(resource: HasMetadata): Replicator?

    /**
     * Watches all resources of the given resource kind
     *
     * @param kind the kind of resources to watch
     *
     * @see [com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.watch]
     */
    fun watch(kind: ResourceKind<out HasMetadata>)

    /**
     * Watches all the given resource kinds
     *
     * @param kinds the kinds of resources to watch
     *
     * @see [com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.watch]
     */
    fun watchAll(kinds: Collection<ResourceKind<out HasMetadata>>)

    /**
     * Watches all resources of the kind specified by the given custom resource definition
     *
     * @param definition the custom resource definition that specifies the custom resources to watch
     */
    fun watch(definition: CustomResourceDefinition)

    /**
     * Creates a watch for the given resource.
     *
     * @param resource that shall be replaced on the cluster
     *
     * @return the resource that was created
     */
    fun watch(resource: HasMetadata, watcher: Watcher<HasMetadata>): Watch?

    /**
     * Stops watching resources of the given resource kind
     *
     * @param kind the kind of resources to ignore
     */
    fun stopWatch(kind: ResourceKind<out HasMetadata>)

    /**
     * Stops watching resources of the kind specified by the given custom resource definition
     *
     * @param definition the custom resource definition that specifies the custom resources to watch
     *
     * @see io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
     * @see io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
     */
    fun stopWatch(definition: CustomResourceDefinition)

    /**
     * Returns all the [ResourceKind]s that are watched in this context.
     *
     * @return all the resource kinds that are watched.
     */
    fun getWatched(): Collection<ResourceKind<out HasMetadata>>

    /**
     * Adds the given resource to this context.
     *
     * @param resource the resource to add
     * @return true if the resource was added
     */
    fun added(resource: HasMetadata): Boolean

    /**
     * Notifies this context that the given resource was removed on the cluster.
     * Removes the given resource from this context.
     *
     * @param resource the resource to remove
     * @return true if the resource was removed
     */
    fun removed(resource: HasMetadata): Boolean

    /**
     * Removes all resources of the given kind in (the cache of) this context.
     *
     * @param kind the kind of resources to invalidate
     */
    fun invalidate(kind: ResourceKind<*>)


    /**
     * Removes all resources in (the cache of) this context.
     */
    fun invalidate()

    /**
     * Notifies the context that the given resource was replaced in the cluster.
     * Replaces the resource with the given new version if it exists.
     * Does nothing otherwise.
     *
     *
     * @param resource the new (version) of the resource
     * @return true if the resource was replaced
     */
    fun replaced(resource: HasMetadata): Boolean

    /**
     * Returns the url of the Dashboard for this context.
     * Throws if the url could not be determined.
     *
     * @return the url of the Dashboard for this context
     */
    fun getDashboardUrl(): String?

    /**
     * Closes and disposes this context.
     */
    fun close()
}
