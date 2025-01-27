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

import com.intellij.json.JsonFileType
import com.intellij.openapi.fileTypes.FileType
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.utils.Serialization
import org.jetbrains.yaml.YAMLFileType

object EditorResourceSerialization {

    const val RESOURCE_SEPARATOR_YAML = "\n---"
    private const val RESOURCE_SEPARATOR_JSON = ",\n"

    /**
     * Returns a list of [HasMetadata] for a given yaml or json string.
     * Several resources are only supported for yaml file type. Trying to deserialize ex. json that would result
     * in several resources throws a [ResourceException].
     *
     * @param jsonYaml string representing kubernetes resources
     * @param fileType yaml- or json file type (no other types are supported)
     * @param currentNamespace the namespace to set to the resulting resources if they have none
     * @return list of [HasMetadata] for the given yaml or json string
     * @throws ResourceException if several resources are to be deserialized to a non-yaml filetype
     *
     * @see isSupported
     * @see RESOURCE_SEPARATOR_YAML
     * @see YAMLFileType.YML
     * @see JsonFileType.INSTANCE
     */
    fun deserialize(jsonYaml: String?, fileType: FileType?, currentNamespace: String?): List<HasMetadata> {
        return if (jsonYaml == null
            || !isSupported(fileType)) {
            emptyList()
        } else {
            val resources = jsonYaml
                .split(RESOURCE_SEPARATOR_YAML)
                .filter { jsonYaml -> jsonYaml.isNotBlank() }
            if (resources.size > 1
                && YAMLFileType.YML != fileType) {
                throw ResourceException(
                    "${fileType?.name ?: "File type"} is not supported for multi-resource documents. Only ${YAMLFileType.YML.name} is.")
            }
            try {
                resources
                    .map { jsonYaml ->
                        setMissingNamespace(currentNamespace, createResource<GenericKubernetesResource>(jsonYaml))
                    }
                    .toList()
            } catch (e: RuntimeException) {
                throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
            }
        }
    }

    private fun setMissingNamespace(namespace: String?, resource: HasMetadata): HasMetadata {
        if (resource.metadata.namespace.isNullOrEmpty()
            && namespace != null) {
            resource.metadata.namespace = namespace
        }
        return resource
    }

    fun serialize(resources: List<HasMetadata>, fileType: FileType?): String? {
        if (fileType == null) {
            return null
        }
        if (resources.size >= 2
            && fileType != YAMLFileType.YML) {
            throw UnsupportedOperationException(
                "${fileType.name} is not supported for multi-resource documents. Only ${YAMLFileType.YML.name} is.")
        }
        return resources
            .mapNotNull { resource -> serialize(resource, fileType) }
            .joinToString("\n")

    }

    private fun serialize(resource: HasMetadata, fileType: FileType): String? {
        return when(fileType) {
            YAMLFileType.YML ->
                Serialization.asYaml(resource).trim()
            JsonFileType.INSTANCE ->
                Serialization.asJson(resource).trim()
            else -> null
        }
    }

    private fun isSupported(fileType: FileType?): Boolean {
        return fileType == YAMLFileType.YML
                || fileType == JsonFileType.INSTANCE
    }
}
