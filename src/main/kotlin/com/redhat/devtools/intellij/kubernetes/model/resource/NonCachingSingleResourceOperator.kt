/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.hasGenerateName
import com.redhat.devtools.intellij.kubernetes.model.util.hasName
import io.fabric8.kubernetes.api.model.APIResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.StatusBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import io.fabric8.kubernetes.client.utils.Serialization
import java.net.HttpURLConnection

/**
 * Offers remoting operations like [get], [replace], [watch] to
 * retrieve, create, replace or watch a resource on the current cluster.
 * API discovery is executed and a [KubernetesClientException] is thrown if resource kind and version are not supported.
 */
class NonCachingSingleResourceOperator(
    private val client: ClientAdapter<out KubernetesClient>,
    private val api: APIResources = APIResources(client)
) {

    /**
     * Returns the latest version of the given resource from cluster. Returns `null` if none was found.
     * Retrieves the resource on the cluster if the given resource has a [io.fabric8.kubernetes.api.model.ObjectMeta.name].
     * Does nothing if this condition is not met.
     *
     * @param resource which is to be requested from cluster
     *
     * @return resource that was retrieved from cluster
     */
    fun get(resource: HasMetadata): HasMetadata? {
        return if (hasName(resource)) {
            val genericKubernetesResource = toGenericKubernetesResource(resource)
            val op = createOperation(resource)
            op.withName(genericKubernetesResource.metadata.name)
                .fromServer()
                .get()
        } else {
            null
        }
    }

    /**
     * Replaces the given resource on the cluster if it exists. Creates a new one if it doesn't.
     * Creates or replaces the resource on the cluster if the given resource has a [io.fabric8.kubernetes.api.model.ObjectMeta.name]. or
     * Creates a resource on the cluster if the given resource has a [io.fabric8.kubernetes.api.model.ObjectMeta.generateName].
     * Does nothing if none of these conditions apply.
     *
     * **Note:** ["createOrReplace method doesn't support generateName for Jobs"](https://github.com/fabric8io/kubernetes-client/issues/2507#issuecomment-708418495)
     * specifies that a `name` is required when using [io.fabric8.kubernetes.client.dsl.MixedOperation.createOrReplace].
     * If there's no `name` you need to use [io.fabric8.kubernetes.client.dsl.MixedOperation.create].
     *
     * @param resource that shall be replaced on the cluster
     * @throws ResourceException if given resource has neither a name nor a generateName
     *
     * @return the resource that was created
     */
    fun replace(resource: HasMetadata): HasMetadata? {
        val genericKubernetesResource = toGenericKubernetesResource(resource)
        val op = createOperation(resource)

        return if (hasName(genericKubernetesResource)) {
            op.createOrReplace(genericKubernetesResource)
        } else if (hasGenerateName(genericKubernetesResource)) {
            op.create(genericKubernetesResource)
        } else {
            throw ResourceException("Could not replace ${resource.kind ?: "resource"}: has neither name nor generateName.")
        }
    }

    /**
     * Creates a watch for the given resource.
     * Watches the resource on the cluster only if the given resource has a [io.fabric8.kubernetes.api.model.ObjectMeta.name].
     * Does nothing if this condition is not met.
     *
     * @param resource that shall be replaced on the cluster
     *
     * @return the resource that was created
     */
    fun watch(resource: HasMetadata, watcher: Watcher<HasMetadata>): Watch? {
        val genericKubernetesResource = toGenericKubernetesResource(resource)
        val op = createOperation(genericKubernetesResource)
        return if (hasName(genericKubernetesResource)) {
            op.watch(GenericKubernetesResourceWatcherAdapter(watcher))
        } else {
            null
        }
    }

    private fun createOperation(resource: HasMetadata): NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> {
        val context = createResourceDefinitionContext(resource)
        val inNamespace = resourceOrClientNamespace(resource, client)
        return if (context.isNamespaceScoped
            && true == inNamespace?.isNotEmpty()
        ) {
            client.get()
                .genericKubernetesResources(context)
                .inNamespace(inNamespace)
        } else {
            client.get()
                .genericKubernetesResources(context)
        }
    }

    private fun createResourceDefinitionContext(resource: HasMetadata): ResourceDefinitionContext {
        val kind = resource.kind
        val group = ApiVersionUtil.trimGroupOrNull(resource.apiVersion)
        val version = ApiVersionUtil.trimVersion(resource.apiVersion)
        val apiResource = getAPIResource(resource)
        return ResourceDefinitionContext.Builder()
            .withKind(kind)
            .withGroup(group)
            .withVersion(version)
            .withNamespaced(apiResource.namespaced)
            .withPlural(apiResource.name)
            .build()
    }


    private fun getAPIResource(resource: HasMetadata): APIResource {
        val kind = resource.kind
        val group = ApiVersionUtil.trimGroupOrNull(resource.apiVersion)
        val version = ApiVersionUtil.trimVersion(resource.apiVersion)
        return getAPIResource(kind, group, version)
    }

    /**
     * Returns a [GenericKubernetesResource] for a given [HasMetadata].
     * The given resource is returned as if it is a [GenericKubernetesResource].
     *
     * @param resource a HasMetadata to convert
     * @return a GenericKubernetesResource
     */
    private fun toGenericKubernetesResource(resource: HasMetadata): GenericKubernetesResource {
        return if (resource is GenericKubernetesResource) {
            resource
        } else {
            val yaml = Serialization.asYaml(resource)
            Serialization.unmarshal(yaml, GenericKubernetesResource::class.java)
        }
    }

    /**
     * Returns the [APIResource] for the given kind, group and version. Throws [KubernetesClientException] if none was found.
     *
     * @param kind the resource kind of the APIResource
     * @param group the resource group of the APIResource
     * @param version the resource version of the APIResource
     *
     * @return the APIResource for the given kind, group and version
     * @throws KubernetesClientException if none was found
     */
    private fun getAPIResource(kind: String, group: String?, version: String): APIResource {
        return api.get(kind, group, version)
            ?: throw throw createKubernetesException(kind, group, version)
    }

    private fun resourceOrClientNamespace(resource: HasMetadata?, client: ClientAdapter<out KubernetesClient>): String? {
        return if (true == resource?.metadata?.namespace?.isNotBlank()) {
            resource.metadata.namespace
        } else {
            client.namespace
        }
    }

    private fun createKubernetesException(
        kind: String?,
        group: String?,
        version: String?
    ): KubernetesClientException {
        return KubernetesClientException(
            StatusBuilder()
                .withMessage("Unsupported kind $kind in version $group/$version")
                .withKind(kind)
                .withApiVersion("$group/$version")
                .withCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE)
                .build()
        )
    }

    private class GenericKubernetesResourceWatcherAdapter(private val watcher: Watcher<HasMetadata>): Watcher<GenericKubernetesResource> {
        override fun eventReceived(action: Watcher.Action?, resource: GenericKubernetesResource?) {
            watcher.eventReceived(action, resource)
        }

        override fun onClose(cause: WatcherException?) {
            watcher.onClose(cause)
        }
    }
}