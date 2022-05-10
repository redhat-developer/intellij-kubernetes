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

import com.redhat.devtools.intellij.kubernetes.model.resource.APIResources.APIResource
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionContextFactory
import com.redhat.devtools.intellij.kubernetes.model.util.isCustomResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.StatusBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.dsl.VisitFromServerGetWatchDeleteRecreateWaitApplicable
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import io.fabric8.kubernetes.client.utils.Serialization
import java.net.HttpURLConnection

/**
 * Offers remoting operations like [get], [replace], [watch] to
 * retrieve, create, replace or watch a resource on the current cluster.
 * API discovery is executed and a [KubernetesClientException] is thrown if resource kind and version are not supported.
 */
class NonCachingSingleResourceOperator(private val client: KubernetesClient, private val api: APIResources = APIResources(client)) {

    /**
     * Returns the latest version of the given resource from cluster. Returns `null` if none was found.
     *
     * @param resource which is to be requested from cluster
     *
     * @return resource that was retrieved from cluster
     */
    fun get(resource: HasMetadata): HasMetadata? {
        return if (isCustomResource(resource)) {
            createCustomResourceOperation(toGenericKubernetesResource(resource), client)?.fromServer()?.get()
        } else {
            createLegacyResourceOperation(resource, client)?.fromServer()?.get()
        }
    }

    /**
     * Replaces the given resource on the cluster if it exists. Creates a new one if it doesn't.
     *
     * @param resource that shall be replaced on the cluster
     *
     * @return the resource that was created
     */
    fun replace(resource: HasMetadata): HasMetadata? {
        return if (isCustomResource(resource)) {
            val genericKubernetesResource = toGenericKubernetesResource(resource)
            createCustomResourceOperation(genericKubernetesResource, client)
                ?.createOrReplace(genericKubernetesResource)
        } else {
            createLegacyResourceOperation(resource, client)
                ?.createOrReplace()
        }
    }

    /**
     * Creates a watch for the given resource.
     *
     * @param resource that shall be replaced on the cluster
     *
     * @return the resource that was created
     */
    fun watch(resource: HasMetadata, watcher: Watcher<HasMetadata>): Watch? {
        return if (isCustomResource(resource)) {
            createCustomResourceOperation(toGenericKubernetesResource(resource), client)
                ?.watch(GenericKubernetesResourceWatcherAdapter(watcher))
        } else {
            createLegacyResourceOperation(resource, client)
                ?.watch(watcher)
        }
    }

    private fun toGenericKubernetesResource(resource: HasMetadata): GenericKubernetesResource {
        return if (resource is GenericKubernetesResource) {
            resource
        } else {
            val yaml = Serialization.asYaml(resource)
            Serialization.unmarshal(yaml, GenericKubernetesResource::class.java)
        }
    }

    /**
     * Returns an operation for custom resources given a [GenericKubernetesResource] and [KubernetesClient].
     * This operation handles custom resources.
     *
     * @param resource the resource to handle
     *
     * @return the operation that's able to handle a custom resource
     * @throws KubernetesClientException if an error occurs requesting or api discovery fails
     *
     * As for kubernetes-client 5.7.2 legacy resources are handled via [KubernetesClient.resource] while
     * custom resources via [KubernetesClient.genericKubernetesResources].
     * This is not needed starting with kubernetes-client >=5.9 which can handle
     * legacy and custom resource via calling [KubernetesClient.resource].
     */
    private fun createCustomResourceOperation(
        resource: GenericKubernetesResource, client: KubernetesClient
    ): Resource<GenericKubernetesResource>? {
        val kind = resource.kind
        val group = ApiVersionUtil.trimGroupOrNull(resource.apiVersion)
        val version = ApiVersionUtil.trimVersion(resource.apiVersion)
        val apiResource = getAPIResource(resource)
        val context = CustomResourceDefinitionContextFactory.create(
            kind,
            group,
            version,
            apiResource
        ) ?: return null

        return if (context.isNamespaceScoped) {
            val namespace = resourceOrClientNamespace(resource, client)
            client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .withName(resource.metadata.name)
        } else {
            client.genericKubernetesResources(context)
                .withName(resource.metadata.name)
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

    /**
     * Returns an operation for legacy resources given a [HasMetadata] and [KubernetesClient].
     * This operation handles legacy/built-in resources.
     *
     * @param resource the resource to handle
     * @return the operation that's able to handle legacy resources
     *
     * As for kubernetes-client 5.7.2 legacy resources are handled via [KubernetesClient.resource] while
     * custom resources via [KubernetesClient.genericKubernetesResources].
     * This is not needed starting with kubernetes-client >=5.9 which can handle
     * legacy and custom resource when calling [KubernetesClient.resource].
     */
    private fun createLegacyResourceOperation(
        resource: HasMetadata, client: KubernetesClient
    ): VisitFromServerGetWatchDeleteRecreateWaitApplicable<HasMetadata>? {
        if (resource is GenericKubernetesResource) {
            return null
        }
        val apiResource = getAPIResource(resource)
        return client.resource(resource).apply {
            if (apiResource.namespaced) {
                val namespace = resourceOrClientNamespace(resource, client)
                this.inNamespace(namespace)
            }
        }
    }

    private fun getAPIResource(resource: HasMetadata): APIResource {
        val kind = resource.kind
        val group = ApiVersionUtil.trimGroupOrNull(resource.apiVersion)
        val version = ApiVersionUtil.trimVersion(resource.apiVersion)
        return getAPIResource(kind, group, version)
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

    private fun resourceOrClientNamespace(resource: HasMetadata?, client: KubernetesClient): String {
        return if (true == resource?.metadata?.namespace?.isNotBlank()) {
            resource.metadata.namespace
        } else {
            client.namespace
        }
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