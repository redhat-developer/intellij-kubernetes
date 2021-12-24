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
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClientException
import java.lang.RuntimeException

object EditorResourceFactory {

    /**
     * Returns a [HasMetadata] for a given editor and [Clients] instance.
     *
     * @param editor to retrieve the json/yaml from
     * @param definitions custom resource definitions that exist on the cluster
     * @return [HasMetadata] for the given editor and clients
     *
     */
    fun create(editor: FileEditor, definitions: Collection<CustomResourceDefinition>?): HasMetadata? {
        return create(getDocument(editor), definitions)
    }

    /**
     * Returns a [HasMetadata] for a given [Document] and [Clients] instance.
     *
     * @param document to retrieve the json/yaml from
     * @param definitions custom resource definitions that exist on the cluster
     * @return [HasMetadata] for the given editor and clients
     *
     */
    fun create(document: Document?, definitions: Collection<CustomResourceDefinition>?): HasMetadata? {
        return if (document?.text == null) {
            null
        } else {
            create(document.text, definitions)
        }
    }

    /**
     * Returns a [HasMetadata] for a given json/yaml and [Clients] instance.
     * The list of available custom resource definitions is retrieved to determined if given resource kind is a
     * custom or standard resource. In the prior case a [GenericCustomResource] is returned and in the latter a
     * built-in sub class of [HasMetadata].
     *
     * @param jsonYaml to deserialize
     * @param definitions custom resource definitions that exist on the cluster
     * @return [HasMetadata] for the given editor and clients
     * @throws ResourceException if the given json/yaml is invalid or the custom resource definitions could not be retrieved
     *
     * @see CustomResourceDefinitionMapping
     */
    private fun create(jsonYaml: String, definitions: Collection<CustomResourceDefinition>?): HasMetadata? {
        if (definitions == null) {
            return null
        }
        val resource = try {
            createResource<HasMetadataResource>(jsonYaml)
        } catch (e: RuntimeException) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }

        return try {
            if (CustomResourceDefinitionMapping.isCustomResource(resource, definitions)) {
                createResource<GenericCustomResource>(jsonYaml)
            } else {
                createResource(jsonYaml)
            }
        } catch(e: RuntimeException) {
            throw ResourceException("invalid kubernetes yaml/json", e.cause)
        }
    }
}