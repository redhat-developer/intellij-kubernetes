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
package com.redhat.devtools.intellij.kubernetes.validation

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import java.io.InputStream
import java.util.stream.Collectors

class KubernetesSchemasProviderFactory : JsonSchemaProviderFactory {

    companion object {
        private const val BASE_DIR = "/schemas/k8s.io"
        private const val INDEX_FILE = "index.txt"
    }

    private val providers: List<KubernetesSchemaProvider> = listOf()
        get() {
            return field.ifEmpty {
                load()
            }
        }

    override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
        return providers.map { it.withProject(project) }
    }

    private fun load(): List<KubernetesSchemaProvider> {
        val input = javaClass.getResourceAsStream("$BASE_DIR/$INDEX_FILE")
        return input.bufferedReader()
            .use { reader ->
                @Suppress("UNCHECKED_CAST")
                reader.lines()
                    .filter { !it.contains(INDEX_FILE) }
                    .map { createProvider("$BASE_DIR/$it") }
                    .filter { it != null }
                    .collect(Collectors.toList()) as List<KubernetesSchemaProvider>
            }
    }

    private fun createProvider(path: String): KubernetesSchemaProvider? {
        val type = createKubernetesTypeInfo(path) ?: return null
        val url = KubernetesSchemasProviderFactory::class.java.getResource(path) ?: return null
        val file = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.convertFromUrl(url)) ?: return null
        return KubernetesSchemaProvider(type, file)
    }

    private fun createKubernetesTypeInfo(path: String): KubernetesTypeInfo? {
        try {
            val schema = javaClass.getResourceAsStream(path) ?: return null
            val apiGroupKind = getApiGroupKind(schema) ?: return null
            return KubernetesTypeInfo(apiGroupKind.first, apiGroupKind.second)
        } catch (e: Exception) {
            logger<KubernetesSchemasProviderFactory>().warn("Could not parse json schema file ${path}.")
            return null
        }
    }

    private fun getApiGroupKind(schema: InputStream): Pair<String, String>? {
        val json: Map<String, Any> = ObjectMapper().readValue(schema, object : TypeReference<Map<String, Any>>() {})
        if (json.isEmpty()) {
            return null
        }
        // if has no x-kubernetes-group-version-kind it's not an openapi type
        @Suppress("UNCHECKED_CAST")
        val groupVersionKinds = json["x-kubernetes-group-version-kind"] as? List<Map<String, String>>
        if (groupVersionKinds == null
            || groupVersionKinds.isEmpty()) {
            return null
        }
        val groupVersionKind = groupVersionKinds[0] // 1st entry
        val apiGroup = getApiGroup(
            groupVersionKind["group"],
            groupVersionKind["version"]
        )
        val kind = groupVersionKind["kind"]
        if (apiGroup == null
            || kind == null) {
            return null
        }
        return Pair(apiGroup, kind)
    }

    private fun getApiGroup(group: String?, version: String?): String? {
        return if (group != "") {
            "$group/$version"
        } else {
            version
        }
    }

}