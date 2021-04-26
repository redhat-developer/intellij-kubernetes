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

import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import java.net.URL

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

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

    /**
     * The master url for this context. This is the url of the cluster for this context.
     */
    val masterUrl: URL

    /**
     * Returns {@code true} if this context is an OpenShift context. This is true for context with an OpenShift cluster.
     */
    fun isOpenShift(): Boolean

    /**
     * Sets the current namespace for this context.
     *
     * @param namespace
     */
    fun setCurrentNamespace(namespace: String): Boolean

    /**
     * Returns the current namespace for this context or {@code null} if there's none.
     */
    fun getCurrentNamespace(): String?

    /**
     * Returns {@code true} if the given resource is the current namespace.
     */
    fun isCurrentNamespace(resource: HasMetadata): Boolean

    /**
     * Deletes the given resources.
     */
    fun delete(resources: List<HasMetadata>)

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
     * @see CustomResourceDefinition
     * @see CustomResourceDefinitionSpec
     */
    fun getAllResources(definition: CustomResourceDefinition): Collection<GenericCustomResource>

    /**
     * Watches all resources of the given resource kind
     *
     * @param kind the kind of resources to ignore
     */
    fun watch(kind: ResourceKind<out HasMetadata>)

    /**
     * Watches all resources of the kind specified by the given custom resource definition
     *
     * @param definition the custom resource definition that specifies the custom resources to watch
     */
    fun watch(definition: CustomResourceDefinition)

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
     * @see CustomResourceDefinition
     * @see CustomResourceDefinitionSpec
     */
    fun stopWatch(definition: CustomResourceDefinition)

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
     * Does nothing otherwiese.
     *
     *
     * @param resource the new (version) of the resource
     * @return true if the resource was replaced
     */
    fun replaced(resource: HasMetadata): Boolean

    /**
     * Closes and disposes this context.
     */
    fun close()
}
