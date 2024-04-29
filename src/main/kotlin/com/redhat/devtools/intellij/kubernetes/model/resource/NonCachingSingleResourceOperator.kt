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
import com.redhat.devtools.intellij.kubernetes.model.util.hasManagedFields
import com.redhat.devtools.intellij.kubernetes.model.util.hasName
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
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
import io.fabric8.kubernetes.client.dsl.base.PatchContext
import io.fabric8.kubernetes.client.dsl.base.PatchType
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import io.fabric8.kubernetes.client.utils.Serialization
import java.net.HttpURLConnection

/**
 * Offers remoting operations like [get], [create],  [replace], [watch] to
 * retrieve, create, replace or watch a resource on the current cluster.
 * API discovery is executed and a [KubernetesClientException] is thrown if resource kind and version are not supported.
 */
class NonCachingSingleResourceOperator(
    private val client: ClientAdapter<out KubernetesClient>,
    private val api: APIResources = APIResources(client),
    private val genericResourceFactory: (HasMetadata) -> GenericKubernetesResource = { resource ->
        val yaml = Serialization.asYaml(resource)
        Serialization.unmarshal(yaml, GenericKubernetesResource::class.java)
    }
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
        if (!hasName(resource)) {
            return null
        }
        val genericKubernetesResource = toGenericKubernetesResource(resource, false)
        val op = createOperation(resource)
        return op
            .withName(genericKubernetesResource.metadata.name)
            .get()
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
        // force clone, patch changes the given resource
        val genericKubernetesResource = toGenericKubernetesResource(resource, true)
        val op = createOperation(resource)
        return if (hasName(genericKubernetesResource)) {
            if (hasManagedFields(genericKubernetesResource)) {
                patch(genericKubernetesResource, op, PatchType.STRATEGIC_MERGE)
            } else {
                patch(genericKubernetesResource, op, PatchType.SERVER_SIDE_APPLY)
            }
        } else if (hasGenerateName(genericKubernetesResource)) {
            create(genericKubernetesResource, op)
        } else {
            throw ResourceException("Could not replace ${resource.kind ?: "resource"}: has neither name nor generateName.")
        }
    }

    fun create(resource: HasMetadata): HasMetadata? {
        // force clone, patch changes the given resource
        val genericKubernetesResource = toGenericKubernetesResource(resource, true)
        val op = createOperation(resource)
        return if (hasName(genericKubernetesResource)
            && !hasManagedFields(genericKubernetesResource)
        ) {
            patch(genericKubernetesResource, op, PatchType.SERVER_SIDE_APPLY)
        } else {
            create(genericKubernetesResource, op)
        }
    }

    private fun create(
        genericKubernetesResource: GenericKubernetesResource,
        op: NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>
    ): GenericKubernetesResource? =
        runWithoutServerSetProperties(genericKubernetesResource) {
            op.resource(genericKubernetesResource).create()
        }

    private fun patch(
        genericKubernetesResource: GenericKubernetesResource,
        op: NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>>,
        patchType: PatchType
    ): HasMetadata? {
        return runWithoutServerSetProperties(genericKubernetesResource) {
            op
                .resource(genericKubernetesResource)
                .patch(
                    PatchContext.Builder()
                        //.withForce(true)
                        .withPatchType(patchType)
                        .build()
                )
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
        val genericKubernetesResource = toGenericKubernetesResource(resource, false)
        if (!hasName(genericKubernetesResource)) {
            return null
        }
        return createOperation(genericKubernetesResource)
            .withName(resource.metadata.name)
            .watch(GenericKubernetesResourceWatcherAdapter(watcher))
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
     * If the given resource is a [GenericKubernetesResource] then the resource is returned as is unless
     * [clone] is `true`. In this case a clone is returned instead.
     * If the given resource is not a [GenericKubernetesResource] then an equivalent [GenericKubernetesResource]
     * is created using serialization.
     *
     * @param resource a HasMetadata to convert
     * @param clone force cloning the given resource
     * @return a GenericKubernetesResource
     */
    private fun toGenericKubernetesResource(resource: HasMetadata, clone: Boolean = false): GenericKubernetesResource {
        return if (resource is GenericKubernetesResource
            && !clone) {
            resource
        } else {
            genericResourceFactory.invoke(resource)
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
