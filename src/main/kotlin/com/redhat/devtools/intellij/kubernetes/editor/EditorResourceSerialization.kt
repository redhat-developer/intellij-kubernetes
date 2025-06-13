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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
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
        return try {
            when {
                jsonYaml == null ->
                    emptyList()

                YAMLFileType.YML == fileType ->
                    yaml2Resources(jsonYaml, currentNamespace)

                JsonFileType.INSTANCE == fileType ->
                    json2Resources(jsonYaml, currentNamespace)

                else ->
                    emptyList()
            }
        } catch (e: RuntimeException) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }
    }

    private fun yaml2Resources(yaml: String, currentNamespace: String?): List<HasMetadata> {
        val resources = yaml
            .split(RESOURCE_SEPARATOR_YAML)
            .filter { yaml ->
                yaml.isNotBlank()
            }
        return resources
            .map { yaml ->
                setMissingNamespace(currentNamespace, createResource<GenericKubernetesResource>(yaml))
            }
            .toList()
    }

    private fun json2Resources(json: String?, currentNamespace: String?): List<HasMetadata> {
        val mapper = ObjectMapper()
        val rootNode = mapper.readTree(json)
        return when {
            rootNode.isArray ->
                (rootNode as ArrayNode)
                    .mapNotNull { node ->
                        setMissingNamespace(currentNamespace, mapper.treeToValue(node, GenericKubernetesResource::class.java))
                    }
                    .toList()
            rootNode.isObject ->
                listOf(
                    setMissingNamespace(currentNamespace,
                        mapper.treeToValue(rootNode, GenericKubernetesResource::class.java)
                    )
                )
            else ->
                emptyList()
        }
    }

    private fun setMissingNamespace(namespace: String?, resource: HasMetadata): HasMetadata {
        if (resource.metadata != null
            && resource.metadata.namespace.isNullOrEmpty()
            && namespace != null) {
            resource.metadata.namespace = namespace
        }
        return resource
    }

    fun serialize(resources: List<HasMetadata>, fileType: FileType?): String? {
        return try {
            when {
                fileType == null ->
                    null

                YAMLFileType.YML == fileType ->
                    resources2yaml(resources)

                JsonFileType.INSTANCE == fileType ->
                    resources2json(resources)

                else ->
                    ""
            }
        } catch (e: RuntimeException) {
            throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
        }
    }

    private fun resources2yaml(resources: List<HasMetadata>): String {
        return resources.joinToString("\n") { resource ->
            Serialization.asYaml(resource).trim()
        }
    }

    private fun resources2json(resources: List<HasMetadata>): String {
        return if (resources.size == 1) {
            Serialization.asJson(resources.first()).trim()
        } else {
            Serialization.asJson(resources).trim()
        }
    }

    private fun isSupported(fileType: FileType?): Boolean {
        return fileType == YAMLFileType.YML
                || fileType == JsonFileType.INSTANCE
    }
}
