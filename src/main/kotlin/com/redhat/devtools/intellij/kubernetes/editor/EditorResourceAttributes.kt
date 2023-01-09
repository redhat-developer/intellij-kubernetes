/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.Disposable
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelListener
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceIdentifier
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient

open class EditorResourceAttributes(
    // for mocking purposes
    private val resourceModel: IResourceModel,
    // for mocking purposes
    private val clusterResourceFactory: (resource: HasMetadata, context: IActiveContext<out HasMetadata, out KubernetesClient>?) -> ClusterResource? =
        ClusterResource.Factory::create,
    // for mocking purposes
    private val attributes: LinkedHashMap<ResourceIdentifier, ResourceAttributes> = linkedMapOf()
) : Disposable {

    var resourceChangedListener: IResourceModelListener? = null

    fun getClusterResource(resource: HasMetadata): ClusterResource? {
        return attributes[ResourceIdentifier(resource)]?.clusterResource
    }

    fun getAllClusterResources(): List<ClusterResource> {
        return attributes.values.mapNotNull { attributes -> attributes.clusterResource }
    }

    fun setLastPushedPulled(resource: HasMetadata?) {
        if (resource == null) {
            return
        }
        getAttributes(resource)?.lastPushedPulled = resource
    }

    fun getLastPulledPushed(resource: HasMetadata?): HasMetadata? {
        return getAttributes(resource)?.lastPushedPulled
    }

    fun setResourceVersion(resource: HasMetadata?) {
        if (resource == null) {
            return
        }
        setResourceVersion(resource)
    }

    fun setResourceVersion(resource: HasMetadata?, version: String? = resource?.metadata?.resourceVersion) {
        if (resource == null) {
            return
        }
        getAttributes(resource)?.resourceVersion = version
    }

    fun getResourceVersion(resource: HasMetadata): String? {
        return getAttributes(resource)?.resourceVersion
    }

    private fun getAttributes(resource: HasMetadata?): ResourceAttributes? {
        if (resource == null) {
            return null
        }
        return attributes[ResourceIdentifier(resource)]
    }

    fun update(resources: List<HasMetadata>) {
        val identifiers = resources
            .map { resource -> ResourceIdentifier(resource) }
            .toSet()
        removeOrphanedAttributes(identifiers)
        addNewAttributes(identifiers)
    }

    private fun removeOrphanedAttributes(new: Set<ResourceIdentifier>) {
        val toRemove = attributes
            .filter { (identifier, _) -> !new.contains(identifier) }
        attributes.keys.removeAll(toRemove.keys)
        toRemove.values.forEach { attributes -> attributes.dispose() }
    }

    private fun addNewAttributes(identifiers: Set<ResourceIdentifier>) {
        val existing = attributes.keys
        val new = identifiers.subtract(existing)
        val toPut = new.associateWith { identifier ->
            ResourceAttributes(identifier.resource)
        }
        attributes.putAll(toPut)
    }

    fun disposeAll() {
        dispose(attributes.keys)
    }

    private fun dispose(identifiers: Collection<ResourceIdentifier>) {
        attributes
            .filter { (resourceIdentifier, _) -> identifiers.contains(resourceIdentifier) }
            .forEach { (_, attributes) ->
                attributes.dispose()
            }
    }


    override fun dispose() {
        disposeAll()
    }

    inner class ResourceAttributes(private val resource: HasMetadata) {

        val clusterResource: ClusterResource? = createClusterResource(resource)

        var lastPushedPulled: HasMetadata? = resource
        var resourceVersion: String? = resource.metadata.resourceVersion

        private fun createClusterResource(resource: HasMetadata): ClusterResource? {
            val context = resourceModel.getCurrentContext()
            return if (context != null) {
                val clusterResource = clusterResourceFactory.invoke(
                    resource,
                    context
                )

                val resourceChangeListener = resourceChangedListener
                if (resourceChangeListener != null) {
                    clusterResource?.addListener(resourceChangeListener)
                }
                clusterResource?.watch()
                clusterResource
            } else {
                null
            }
        }

        fun dispose() {
            clusterResource?.close()
            lastPushedPulled = null
            resourceVersion = null
        }
    }

}
