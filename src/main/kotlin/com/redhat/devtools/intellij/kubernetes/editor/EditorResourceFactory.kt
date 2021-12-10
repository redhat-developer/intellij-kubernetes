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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionMapping
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException

object EditorResourceFactory {

    /**
     * Returns a [HasMetadata] for a given editor and [Clients] instance.
     *
     * @param editor to retrieve the json/yaml from
     * @param clients to retrieve the custom resource definitions from
     * @return [HasMetadata] for the given editor and clients
     *
     */
    fun create(editor: FileEditor, clients: Clients<out KubernetesClient>): HasMetadata? {
        return create(getDocument(editor), clients)
    }

    /**
     * Returns a [HasMetadata] for a given [Document] and [Clients] instance.
     *
     * @param document to retrieve the json/yaml from
     * @param clients to retrieve the custom resource definitions from
     * @return [HasMetadata] for the given editor and clients
     *
     */
    fun create(document: Document?, clients: Clients<out KubernetesClient>): HasMetadata? {
        return if (document?.text == null) {
            null
        } else {
            create(document.text, clients)
        }
    }

    /**
     * Returns a [HasMetadata] for a given json/yaml and [Clients] instance.
     * The list of available custom resource definitions is retrieved to determined if given resource kind is a
     * custom or standard resource. In the prior case a [GenericCustomResource] is returned and in the latter a
     * built-in sub class of [HasMetadata].
     *
     * @param jsonYaml to deserialize
     * @param clients to retrieve the custom resource definitions from
     * @return [HasMetadata] for the given editor and clients
     * @throws ResourceException if the given json/yaml is invalid or the custom resource definitions could not be retrieved
     *
     * @see CustomResourceDefinitionMapping
     */
    private fun create(jsonYaml: String, clients: Clients<out KubernetesClient>): HasMetadata {
        val resource = try {
            createResource<HasMetadataResource>(jsonYaml)
        } catch (e: Exception) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }

        return try {
            val definitions = CustomResourceDefinitionMapping.getDefinitions(clients.get())
            if (CustomResourceDefinitionMapping.isCustomResource(resource, definitions)) {
                createResource<GenericCustomResource>(jsonYaml)
            } else {
                createResource(jsonYaml)
            }
        } catch(e: KubernetesClientException) {
            throw ResourceException("invalid kubernetes yaml/json", e.cause)
        }
    }
}