/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.json.psi.JsonElementGenerator
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLValue

private const val KEY_METADATA = "metadata"
private const val KEY_RESOURCE_VERSION = "resourceVersion"

/**
 * Returns `true` if the given document for the given psi document manager has a kubernetes resource.
 * A yaml/json document is considered to be a kubernetes resource document if it contains
 * - metadata.name
 * - metadata.namespace
 *
 * @param file the file to check if it has a kubernetes resource
 * @param project the project to retrieve the psi manager for
 * @return true if the file has a kubernetes resource
 */
fun hasKubernetesResource(file: VirtualFile?, project: Project): Boolean {
    return isKubernetesResource(getKubernetesResourceInfo(file, project))
}

/**
 * Returns `true` if the given [KubernetesResourceInfo] has the informations required for a kubernetes resource.
 * A yaml/json document is considered to be a kubernetes resource document if it contains
 * - apiGroup
 * - kind
 *
 * @param resourceInfo the resource info to inspect
 */
fun isKubernetesResource(resourceInfo: KubernetesResourceInfo?): Boolean {
    return resourceInfo?.typeInfo?.apiGroup?.isNotBlank() ?: false
            && resourceInfo?.typeInfo?.kind?.isNotBlank() ?: false
}

/**
 * Returns [KubernetesResourceInfo] for the given file and project. Returns `null` if it could not be retrieved.
 *
 * @param file the virtual file to check for holding a kubernetes resource
 * @param project the [Project] to retrieve the [PsiManager] for
 * @return the [KubernetesResourceInfo] for the given file or null
 */
fun getKubernetesResourceInfo(file: VirtualFile?, project: Project): KubernetesResourceInfo? {
    if (file == null) {
        return null
    }
    return try {
        ReadAction.compute<KubernetesResourceInfo, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@compute null
            KubernetesResourceInfo.extractMeta(psiFile)
        }
    } catch (e: RuntimeException) {
        null
    }
}

/**
 * Sets or creates the given [resourceVersion] in the given document for the given [PsiDocumentManager] and [Project].
 * The document is **not** committed to allow further modifications before a commit to happen.
 * Does nothing if the resource version or the document is `null`.
 *
 * @param resourceVersion the resource version to set/create
 * @param document the document to set the resource version to
 * @param manager the [PsiDocumentManager] to use for the operation
 * @param project the [Project] to use
 */
fun setResourceVersion(resourceVersion: String?, document: Document?, manager: PsiDocumentManager, project: Project) {
    if (resourceVersion == null
        || document == null) {
        return
    }
    val metadata = getMetadata(document, manager) ?: return
    createOrUpdateResourceVersion(resourceVersion, metadata, project)
}

private fun createOrUpdateResourceVersion(resourceVersion: String, metadata: PsiElement, project: Project) {
    when (metadata) {
        is YAMLKeyValue -> createOrUpdateResourceVersion(resourceVersion, metadata, project)
        is JsonProperty -> createOrUpdateResourceVersion(resourceVersion, metadata, project)
    }
}

private fun createOrUpdateResourceVersion(resourceVersion: String, metadata: JsonProperty, project: Project) {
    val metadataObject = metadata.value ?: return
    val generator = JsonElementGenerator(project)
    val version = generator.createProperty(KEY_RESOURCE_VERSION, "\"$resourceVersion\"")
    val existingVersion = getResourceVersion(metadata)
    if (existingVersion != null) {
        metadataObject.addAfter(version, existingVersion)
        existingVersion.delete()
    } else {
        metadataObject.addBefore(generator.createComma(), metadataObject.lastChild)
        metadataObject.addBefore(version, metadataObject.lastChild)
    }
}

private fun createOrUpdateResourceVersion(resourceVersion: String, metadata: YAMLKeyValue, project: Project) {
    val metadataObject = metadata.value ?: return
    val existingVersion = getResourceVersion(metadata)
    val generator = YAMLElementGenerator.getInstance(project)
    val version = generator.createYamlKeyValue(KEY_RESOURCE_VERSION, "\"$resourceVersion\"")
    if (existingVersion != null) {
        existingVersion.setValue(version.value!!)
    } else {
        metadataObject.add(generator.createEol())
        metadataObject.add(generator.createIndent(YAMLUtil.getIndentToThisElement(metadataObject)))
        metadataObject.add(version)
    }
}

private fun getContent(file: PsiFile): PsiElement? {
    return when (file) {
        is YAMLFile -> {
            if (file.documents.isEmpty()) {
                return null
            }
            file.documents[0].topLevelValue
        }
        is JsonFile ->
            file.topLevelValue
        else -> null
    }
}

fun getMetadata(document: Document?, psi: PsiDocumentManager): PsiElement? {
    if (document == null) {
        return null
    }
    val file = psi.getPsiFile(document) ?: return null
    val content = getContent(file) ?: return null
    return getMetadata(content) ?: return null
}

private fun getMetadata(content: PsiElement): PsiElement? {
    return when (content) {
        is YAMLValue ->
            content.children
            .filterIsInstance(YAMLKeyValue::class.java)
            .find { it.name == KEY_METADATA }
        is JsonValue ->
            content.children.toList()
                .filterIsInstance(JsonProperty::class.java)
                .find { it.name == KEY_METADATA }
        else ->
            null
    }
}

private fun getResourceVersion(metadata: YAMLKeyValue): YAMLKeyValue? {
    return metadata.value?.children
        ?.filterIsInstance(YAMLKeyValue::class.java)
        ?.find { it.name == KEY_RESOURCE_VERSION }
}

private fun getResourceVersion(metadata: JsonProperty): JsonProperty? {
    return metadata.value?.children?.toList()
        ?.filterIsInstance(JsonProperty::class.java)
        ?.find { it.name == KEY_RESOURCE_VERSION }
}
