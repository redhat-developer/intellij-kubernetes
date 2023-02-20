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
import com.intellij.openapi.editor.Document
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
     * Returns a [HasMetadata] for a given [Document] instance.
     *
     * @param jsonYaml serialized resources
     * @return [HasMetadata] for the given editor and clients
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

    fun serialize(resources: Collection<HasMetadata>, fileType: FileType?): String? {
        if (fileType == null) {
            return null
        }
        if (resources.size >=2 && fileType != YAMLFileType.YML) {
            throw UnsupportedOperationException(
                "${fileType.name} is not supported for multi-resource documents. Only ${YAMLFileType.YML.name} is.")
        }
        return resources
            .mapNotNull { resource -> serialize(resource, fileType) }
            .joinToString(RESOURCE_SEPARATOR_YAML)
    }

    private fun serialize(resource: HasMetadata, fileType: FileType): String? {
        val serializer = when(fileType) {
            YAMLFileType.YML ->
                Serialization.yamlMapper().writerWithDefaultPrettyPrinter()
            JsonFileType.INSTANCE ->
                Serialization.jsonMapper().writerWithDefaultPrettyPrinter()
            else -> null
        }
        return serializer?.writeValueAsString(resource)?.trim()
    }

    private fun isSupported(fileType: FileType?): Boolean {
        return fileType == YAMLFileType.YML
                || fileType == JsonFileType.INSTANCE
    }
}
