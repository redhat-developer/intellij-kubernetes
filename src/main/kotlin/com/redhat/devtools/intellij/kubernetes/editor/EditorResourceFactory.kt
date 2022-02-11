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
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import com.redhat.devtools.intellij.kubernetes.model.util.isApiGroupVersionNotMatchingAnnotation
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata

object EditorResourceFactory {

    /**
     * Returns a [HasMetadata] for a given editor and [Clients] instance.
     *
     * @param editor to retrieve the json/yaml from
     * @return [HasMetadata] for the given editor and clients
     */
    fun create(editor: FileEditor): HasMetadata? {
        return create(getDocument(editor))
    }

    /**
     * Returns a [HasMetadata] for a given [Document] and [Clients] instance.
     *
     * @param document to retrieve the json/yaml from
     * @return [HasMetadata] for the given editor and clients
     */
    fun create(document: Document?): HasMetadata? {
        return if (document?.text == null) {
            null
        } else {
            create(document.text)
        }
    }

    /**
     * Returns a [HasMetadata] for a given json/yaml and [Clients] instance.
     *
     * @param jsonYaml to deserialize
     * @return [HasMetadata] for the given editor and clients
     * @throws ResourceException if the given json/yaml is invalid or the custom resource definitions could not be retrieved
     *
     * @see CustomResourceDefinitions
     */
    private fun create(jsonYaml: String): HasMetadata? {
        return try {
            val resource: HasMetadata = createResource(jsonYaml)
            if (isApiGroupVersionNotMatchingAnnotation(resource)) {
                // custom resource (bug in kubernetes-client, need to unmarshall again)
                createResource(jsonYaml, GenericKubernetesResource::class.java)
            } else {
                resource
            }
        } catch (e: RuntimeException) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }
    }
}