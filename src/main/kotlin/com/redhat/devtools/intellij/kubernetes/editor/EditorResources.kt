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
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceIdentifier
import io.fabric8.kubernetes.api.model.HasMetadata
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class EditorResources(
    // for mocking purposes
    private val resourceModel: IResourceModel,
    private val createEditorResource: (resource: HasMetadata, resourceModel: IResourceModel, resourceChangedListener: IResourceModelListener?) -> EditorResource =
        { resource, model, listener -> EditorResource(resource, model, listener) }
) : Disposable {
    var resourceChangedListener: IResourceModelListener? = null
    private val resources: LinkedHashMap<ResourceIdentifier, EditorResource> = linkedMapOf()

    /** mutex to exclude concurrent execution of push & watch notification **/
    private val resourceChangeMutex = ReentrantLock()

    fun setDeleted(resource: HasMetadata): Collection<EditorResource> {
        val identifier = ResourceIdentifier(resource)
        return resourceChangeMutex.withLock {
            val editorResource = resources[identifier]
            editorResource?.setState(DeletedOnCluster())
            this.resources.values
        }
    }

    fun setResources(new: List<HasMetadata>): Collection<EditorResource> {
        val identifiers = new
            .map { resource -> ResourceIdentifier(resource) }
            .toSet()
        return resourceChangeMutex.withLock {
            removeOrphanedEditorResources(identifiers)
            updateEditorResources(new)
            addNewEditorResources(identifiers)
            this.resources.values
        }
    }

    private fun removeOrphanedEditorResources(new: Set<ResourceIdentifier>) {
        resourceChangeMutex.withLock {
            val toRemove = resources
                .filter { (identifier, editorResource) ->
                    // remove editor resource for old resource that doesn't exist anymore
                    !new.contains(identifier)
                            // or editor resource that was disposed (ex. change in namespace, context)
                            || editorResource.disposed
                }
            resources.keys.removeAll(toRemove.keys)
            toRemove.values.forEach { editorResource ->
                editorResource.dispose()
            }
        }
    }

    private fun addNewEditorResources(identifiers: Set<ResourceIdentifier>) {
        val existing = resources.keys
        val new = identifiers.subtract(existing)
        val toPut = new.associateWith { identifier ->
            createEditorResource.invoke(identifier.resource, resourceModel, resourceChangedListener)
        }
        resources.putAll(toPut)
    }

    private fun updateEditorResources(newResources: Collection<HasMetadata>) {
        resourceChangeMutex.withLock {
            this.resources
                .forEach { (identifier, editorResource) ->
                    val newResource = getByIdentifier(identifier, newResources) ?: return
                    editorResource.setResource(newResource)
                }
        }
    }

    private fun getByIdentifier(identifier: ResourceIdentifier, resources: Collection<HasMetadata>): HasMetadata? {
        return resources.find { resource ->
            identifier == ResourceIdentifier(resource)
        }
    }

    fun getAllResources(): List<HasMetadata> {
        return resourceChangeMutex.withLock {
            resources.values.map { editorResource ->
                editorResource.getResource()
            }
        }
    }

    fun getAllResourcesOnCluster(): List<HasMetadata> {
        return getAllEditorResources().mapNotNull { editorResource ->
            editorResource.getResourceOnCluster()
        }
    }

    /** for testing purposes */
    protected open fun getAllEditorResources(): List<EditorResource> {
        return resourceChangeMutex.withLock {
            resources.values.toList()
        }
    }

    private fun getEditorResource(resource: HasMetadata): EditorResource? {
        return resourceChangeMutex.withLock {
            resources[ResourceIdentifier(resource)]
        }
    }

    fun hasResource(resource: HasMetadata): Boolean {
        return resourceChangeMutex.withLock {
            getEditorResource(resource) != null
        }
    }

    fun pushAll(filter: (editorResource: EditorResource) -> Boolean): List<EditorResource> {
        val toPush = getAllEditorResources()
            .filter(filter)
        return toPush.map { editorResource ->
            editorResource.push()
            editorResource
        }
    }

    fun push(resource: HasMetadata): EditorResource? {
        val editorResource = getEditorResource(resource)
            ?: return null
            editorResource.push()
        return editorResource
    }

    fun pull(resource: HasMetadata): EditorResource? {
        val editorResource = getEditorResource(resource)
            ?: return null
        editorResource.pull()
        return editorResource
    }

    fun watchAll() {
        getAllEditorResources().forEach { clusterResource ->
            clusterResource.watch()
        }
    }

    fun stopWatchAll() {
        getAllEditorResources().forEach { clusterResource ->
            clusterResource.stopWatch()
        }
    }

    override fun dispose() {
        disposeAll()
    }

    fun disposeAll() {
        resourceChangeMutex.withLock {
            getAllEditorResources().forEach { editorResource ->
                editorResource.dispose()
            }
        }
    }
}
