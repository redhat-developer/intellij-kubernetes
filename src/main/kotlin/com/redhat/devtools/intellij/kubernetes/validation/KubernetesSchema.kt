/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.validation

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object KubernetesSchema {
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * downloaded from https://github.com/yannh/kubernetes-json-schema/tree/master/v1.31.10-standalone-strict
     */
    private const val SCHEMA_BASE_PATH = "/schemas/k8s.io"

    fun get(kind: String, apiVersion: String): String? {
        if (kind.isBlank()
            || apiVersion.isBlank()) {
            logger<KubernetesSchema>().debug("Invalid parameters: kind='$kind', apiVersion='$apiVersion'")
            return null
        }

        val schemaKey = "$apiVersion/$kind"
        return cache[schemaKey] ?: run {
            val schema = load(kind, apiVersion)
            if (schema != null) {
                cache[schemaKey] = schema
            }
            schema
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun load(kind: String, apiVersion: String): String? {
        // Try different naming patterns to find the schema file
        val possibleFileNames = getPossibleFileNames(kind, apiVersion)

        return possibleFileNames.asSequence()
            .mapNotNull { fileName -> load(fileName) }
            .firstOrNull()
            .also { schema ->
                if (schema == null) {
                    logger<KubernetesSchema>().warn("No schema found for kind: '$kind' with apiVersion: '$apiVersion'")
                }
            }
    }

    private fun load(fileName: String): String? {
        val resourcePath = "$SCHEMA_BASE_PATH/$fileName"
        logger<KubernetesSchema>().debug("Trying to load schema from $resourcePath")

        return try {
            return loadSchema(resourcePath)
                ?: loadSchema(resourcePath.removePrefix("/"))
        } catch (e: IOException) {
            logger<KubernetesSchema>().debug("Failed to load schema from $resourcePath", e)
            null
        }
    }

    private fun loadSchema(path: String): String? {
        val inputStream = KubernetesSchema::class.java.getResourceAsStream(path)
        return inputStream?.use { stream ->
            val schema = stream.readBytes().toString(Charsets.UTF_8)
            logger<KubernetesSchema>().info("Successfully loaded schema from $path")
            schema
        }
    }

    /**
     * Returns possible file names for a given kind and apiVersion.
     * This function attempts to generate several possible file names based on the
     * provided `kind` and `apiVersion`. The generated names are ordered by likelihood
     * of a match, starting with the most specific (including group and version) and becoming more general.
     *
     * @param kind The kind of the Kubernetes resource (e.g., "Pod", "Deployment").
     * @param apiVersion The apiVersion of the Kubernetes resource (e.g., "v1", "apps/v1").
     * @return A list of possible schema file names, ordered by specificity.
     */
    internal fun getPossibleFileNames(kind: String, apiVersion: String): List<String> {
        val kindLower = kind.lowercase()
        val fileNames = mutableListOf<String>()

        // Parse apiVersion to extract group and version
        val group = ApiVersionUtil.trimGroupOrNull(apiVersion) ?: ""
        val version = ApiVersionUtil.trimVersion(apiVersion)

        // Pattern 1: kind-group-version.json (e.g., deployment-apps-v1.json)
        if (group.isNotEmpty()) {
            fileNames.add("$kindLower-$group-$version.json")
        }

        // Pattern 2: kind-version.json (e.g., pod-v1.json)
        if (version.isNotEmpty()) {
            fileNames.add("$kindLower-$version.json")
        }

        // Pattern 3: kind.json (e.g., deployment.json)
        fileNames.add("$kindLower.json")
        return fileNames
    }
}