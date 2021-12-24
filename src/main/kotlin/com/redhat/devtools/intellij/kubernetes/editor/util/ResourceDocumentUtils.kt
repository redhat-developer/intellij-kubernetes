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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLValue

const val KEY_METADATA = "metadata"
const val KEY_RESOURCE_VERSION = "resourceVersion"

/**
 * Returns `true` if the given document for the given psi document manager has a kubernetes resource.
 * A yaml/json document is considered to be a kubernetes resource document if it contains
 * - metadata.name
 * - metadata.namespace
 *
 * @param document the document to check for being a kubernetes resource
 * @param psiDocumentManager the psi document manager to use for inspection
 * @return true if the document has a kubernetes resource
 */
fun hasKubernetesResource(document: Document?, psiDocumentManager: PsiDocumentManager): Boolean {
    return isKubernetesResource(getKubernetesResourceInfo(document, psiDocumentManager))
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
 * Returns [KubernetesResourceInfo] for the given document and psi document manager
 *
 * @param document the document to check for being a kubernetes resource
 * @param psiDocumentManager the psi document manager to use for inspection
 */
fun getKubernetesResourceInfo(document: Document?, psiDocumentManager: PsiDocumentManager): KubernetesResourceInfo? {
    if (document == null) {
        return null
    }
    return try {
        ReadAction.compute<KubernetesResourceInfo, RuntimeException> {
            val psiFile = psiDocumentManager.getPsiFile(document)
            KubernetesResourceInfo.extractMeta(psiFile)
        }
    } catch (e: RuntimeException) {
        null
    }
}

fun getResourceVersion(document: Document?, manager: PsiDocumentManager): String? {
    if (document == null) {
        return null
    }
    val file = manager.getPsiFile(document) ?: return null
    val content = getContent(file) ?: return null
    val metadata = getMetadata(content) ?: return null
    return getResourceVersion(metadata)
}

private fun getResourceVersion(metadata: PsiElement): String? {
    return when (metadata) {
        is YAMLKeyValue -> getResourceVersion(metadata)?.value?.text
        is JsonProperty -> getResourceVersion(metadata)?.value?.text
        else -> null
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
    val file = manager.getPsiFile(document) ?: return
    val content = getContent(file) ?: return
    val metadata = getMetadata(content) ?: return
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
