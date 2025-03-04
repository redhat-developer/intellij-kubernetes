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

import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonElementGenerator
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue
import java.util.*


private const val KEY_METADATA = "metadata"
private const val KEY_DATA = "data"
private const val KEY_BINARY_DATA = "binaryData"
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

fun isKubernetesResource(kind: String, info: KubernetesTypeInfo?): Boolean {
    return info?.apiGroup?.isNotBlank() != null
            && kind == info.kind
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
            KubernetesResourceInfo.create(psiFile)
        }
    } catch (e: RuntimeException) {
        null
    }
}

fun getChildren(document: YAMLDocument): List<YAMLPsiElement> {
    val topLevelValue: YAMLValue = document.topLevelValue ?: return emptyList()

    return when (topLevelValue) {
        is YAMLMapping ->
            topLevelValue.keyValues.toList()
        is YAMLSequence ->
            topLevelValue.items
        else ->
            emptyList()
    }
}

fun getChildren(file: JsonFile): List<JsonElement> {
    return file.allTopLevelValues
}

/**
 * Returns the [PsiElement] named "metadata" within the children of the given [PsiElement].
 * Only [YAMLKeyValue] and [JsonProperty] are supported. Returns `null` otherwise.
 *
 * @param element the PsiElement whose "metadata" child should be found.
 * @return the PsiElement named "metadata"
 */
private fun getMetadata(parent: PsiElement): PsiElement? {
    return getElement(KEY_METADATA, parent)
}

private fun getElement(key: String, parent: PsiElement): PsiElement? {
    return when (parent) {
        is YAMLValue ->
            parent.children
                .filterIsInstance<YAMLKeyValue>()
                .find { it.name == key }
        is JsonValue ->
            parent.children.toList()
                .filterIsInstance<JsonProperty>()
                .find { it.name == key }
        else ->
            null
    }
}

/**
 * Returns the [PsiElement] named "data" within the children of the given [PsiElement].
 * Only [YAMLKeyValue] and [JsonProperty] are supported. Returns `null` otherwise.
 *
 * @param element the PsiElement whose "data" child should be found.
 * @return the PsiElement named "data"
 */
fun getDataValue(element: PsiElement): PsiElement? {
    val dataElement = element.children
        .filterIsInstance<PsiNamedElement>()
        .find { it.name == KEY_DATA }
    return when (dataElement) {
        is YAMLKeyValue ->
            dataElement.value
        is JsonProperty ->
            dataElement.value
        else ->
            null
    }
}

/**
 * Returns the [PsiElement] named "binaryData" within the children of the given [PsiElement].
 * Only [YAMLKeyValue] and [JsonProperty] are supported. Returns `null` otherwise.
 *
 * @param element the PsiElement whose "binaryData" child should be found.
 * @return the PsiElement named "binaryData"
 */
fun getBinaryData(element: PsiElement): PsiElement? {
    return when (element) {
        is YAMLPsiElement ->
            element.children
                .filterIsInstance<YAMLKeyValue>()
                .find { it.name == KEY_BINARY_DATA }
                ?.value
        is JsonElement ->
            element.children.toList()
                .filterIsInstance<JsonProperty>()
                .find { it.name == KEY_BINARY_DATA }
                ?.value
        else ->
            null
    }
}

/**
 * Returns a base64 decoded String for the given base64 encoded String.
 * Returns `null` if decoding fails.
 *
 * @param value the string to be decoded
 * @return a decoded String for the given base64 encoded String.
 */
fun decodeBase64(value: String?): String? {
    val bytes = decodeBase64ToBytes(value) ?: return null
    return String(bytes)
}

/**
 * Returns base64 decoded bytes for the given base64 encoded string.
 * Returns `null` if decoding fails.
 *
 * @param value the string to be decoded
 * @return decoded bytes for the given base64 encoded string.
 */
fun decodeBase64ToBytes(value: String?): ByteArray? {
    if (Strings.isEmptyOrSpaces(value)) {
        return value?.toByteArray()
    }
    return try {
        Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Returns the base64 encoded string of the given string.
 * Returns `null` if encoding fails.
 *
 * @param value the string to be encoded
 * @return the base64 encoded string for the given string.
 */
fun encodeBase64(value: String): String? {
    if (Strings.isEmptyOrSpaces(value)) {
        return value
    }
    return try {
        val bytes = Base64.getEncoder().encode(value.toByteArray())
        String(bytes)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Returns the String value of the given [YAMLKeyValue] or [JsonProperty].
 *
 * @param element the psi element to retrieve the startOffset from
 * @return the startOffset in the value of the given psi element
 */
fun getValue(element: PsiElement): String? {
    return when (element) {
        is YAMLKeyValue -> element.value?.text
        is JsonProperty -> element.value?.text
        else -> null
    }
}

/**
 * Returns the startOffset in the [YAMLValue] or [JsonValue] of the given [PsiElement].
 * Returns `null` otherwise.
 *
 * @param element the psi element to retrieve the startOffset from
 * @return the startOffset in the value of the given psi element
 */
fun getStartOffset(element: PsiElement): Int? {
    return when (element) {
        is YAMLKeyValue -> element.value?.textRange?.startOffset
        is JsonProperty -> element.value?.textRange?.startOffset
        else -> null
    }
}

fun setValue(value: String, element: PsiElement) {
    val newElement = when (element) {
        is YAMLKeyValue ->
            YAMLElementGenerator.getInstance(element.project).createYamlKeyValue(element.keyText, value)
        is JsonProperty ->
            JsonElementGenerator(element.project).createProperty(element.name, value)
        else ->
            null
    } ?: return
    element.parent.addAfter(newElement, element)
    element.delete()
}

private fun getResourceVersion(metadata: YAMLKeyValue): YAMLKeyValue? {
    return metadata.value?.children
        ?.filterIsInstance<YAMLKeyValue>()
        ?.find { it.name == KEY_RESOURCE_VERSION }
}

private fun getResourceVersion(metadata: JsonProperty): JsonProperty? {
    return metadata.value?.children?.toList()
        ?.filterIsInstance<JsonProperty>()
        ?.find { it.name == KEY_RESOURCE_VERSION }
}

/**
 * Returns a [ResourceEditor] for the given [FileEditor] if it exists. Returns `null` otherwise.
 * The editor exists if it is contained in the user data for the given editor or its file.
 *
 * @param editor for which an existing [ResourceEditor] is returned.
 * @return [ResourceEditor] that exists.
 *
 * @see [FileEditor.getUserData]
 * @see [VirtualFile.getUserData]
 */
fun getExistingResourceEditor(editor: FileEditor?): ResourceEditor? {
    if (editor == null) {
        return null
    }
    return editor.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
        ?: getExistingResourceEditor(editor.file)
}

/**
 * Returns a [ResourceEditor] for the given [VirtualFile] if it exists. Returns `null` otherwise.
 * The editor exists if it is contained in the user data for the given file.
 *
 * @param file for which an existing [VirtualFile] is returned.
 * @return [ResourceEditor] that exists.
 *
 * @see [VirtualFile.getUserData]
 */
fun getExistingResourceEditor(file: VirtualFile?): ResourceEditor? {
    return file?.getUserData(ResourceEditor.KEY_RESOURCE_EDITOR)
}

