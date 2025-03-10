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
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLValue

private const val KEY_METADATA = "metadata"
private const val KEY_NAME = "name"
private const val KEY_LABELS = "labels"
private const val KEY_SPEC = "spec"
private const val KEY_SELECTOR = "selector"
private const val KEY_TEMPLATE = "template"
private const val KEY_BINARY_DATA = "binaryData"
private const val KEY_DATA = "data"


fun PsiFile.getAllElements(): List<PsiElement> {
    return when(this) {
        is YAMLFile -> this.documents.mapNotNull { document -> document.topLevelValue }
        is JsonFile -> this.allTopLevelValues
        else -> emptyList()
    }
}

fun PsiElement.getKey(): PsiElement?{
    return when(this) {
        is YAMLKeyValue -> this.key
        is JsonProperty -> this.nameElement
        else -> null
    }
}

fun PsiElement.getValue(): PsiElement?{
    return when(this) {
        is YAMLKeyValue -> this.value
        is JsonProperty -> this.value
        else -> null
    }
}

fun PsiFile.hasKubernetesResource(): Boolean {
    return KubernetesTypeInfo.create(this) != null
}

fun PsiElement.isKubernetesResource(): Boolean {
    return when (this) {
        is PsiFile -> false
        is YAMLDocument -> false
        else -> KubernetesTypeInfo.create(this) != null
    }
}

fun PsiElement.getKubernetesTypeInfo(): KubernetesTypeInfo? {
    return when {
        this is PsiFile || this is YAMLDocument -> null // KubernetesTypeInfo.create() creates a type for nested elements
        else -> KubernetesTypeInfo.create(this)
    }
}

fun PsiElement.getResource(): PsiElement? {
    return when {
        this.isKubernetesResource() ->
            this
        parent != null ->
            parent.getResource()
        else ->
            null
    }
}

fun PsiElement.getResourceName(): PsiElement? {
    return when(this) {
        is YAMLMapping -> getResourceName()
        is JsonObject -> getResourceName()
        else -> null
    }
}

fun YAMLMapping.getResourceName(): YAMLValue? {
    return getMetadata()?.getKeyValueByKey(KEY_NAME)
        ?.value
}

fun JsonObject.getResourceName(): JsonValue? {
    return getMetadata()?.findProperty(KEY_NAME)
        ?.value
}

fun JsonObject.getMetadata(): JsonObject? {
    return this.findProperty(KEY_METADATA)
        ?.value as? JsonObject
}

fun YAMLMapping.getMetadata(): YAMLMapping? {
    return this.getKeyValueByKey(KEY_METADATA)
        ?.value as? YAMLMapping
}

fun PsiElement.isLabels(): Boolean {
    return when (this) {
        is YAMLKeyValue -> KEY_LABELS == this.keyText
        is JsonProperty -> KEY_LABELS == this.name
        else -> false
    }
}

fun PsiElement.hasLabels(): Boolean {
    return when(this) {
        is YAMLMapping -> hasLabels()
        is JsonObject -> hasLabels()
        else -> false
    }
}

fun YAMLMapping.hasLabels(): Boolean {
    return true == this.getLabels()?.keyValues?.isNotEmpty()
}

fun JsonObject.hasLabels(): Boolean {
    return true == this.getLabels()?.propertyList?.isNotEmpty()
}

fun PsiElement.getLabels(): PsiElement? {
    return when(this) {
        is YAMLMapping -> this.getLabels()
        is JsonObject -> this.getLabels()
        else -> null
    }
}

fun YAMLMapping.getLabels(): YAMLMapping? {
    return this.getMetadata()
        ?.getKeyValueByKey(KEY_LABELS)
        ?.value as? YAMLMapping
}

fun JsonObject.getLabels(): JsonObject? {
    return this.getMetadata()
        ?.findProperty(KEY_LABELS)?.value as? JsonObject
}

fun PsiElement.hasSelector(): Boolean {
    return this.getSelector() != null
}

fun PsiElement.isSelector(): Boolean {
    return when (this) {
        is YAMLKeyValue -> KEY_SELECTOR == this.keyText
        is JsonProperty -> KEY_SELECTOR == this.name
        else -> false
    }
}

fun PsiElement.getSelector(): PsiElement? {
    return when (this) {
        is YAMLMapping -> this.getSelector()
        is JsonObject -> this.getSelector()
        else -> null
    }
}

fun YAMLMapping.getSelector(): YAMLMapping? {
    return (this.getKeyValueByKey(KEY_SPEC)?.value as? YAMLMapping)
        ?.getKeyValueByKey(KEY_SELECTOR)?.value as? YAMLMapping
}

fun JsonObject.getSelector(): JsonObject? {
    return (this.findProperty(KEY_SPEC)?.value as? JsonObject)
        ?.findProperty(KEY_SELECTOR)?.value as? JsonObject
}

fun PsiElement.hasTemplate(): Boolean {
    return this.getTemplate() != null
}

fun PsiElement.getTemplate(): PsiElement? {
    return when(this) {
        is YAMLMapping -> this.getTemplate()
        is JsonObject -> this.getTemplate()
        else ->
            null
    }
}

fun YAMLMapping.getTemplate(): YAMLMapping? {
    return (this.getKeyValueByKey(KEY_SPEC)?.value as? YAMLMapping)
        ?.getKeyValueByKey(KEY_TEMPLATE)?.value as? YAMLMapping
}

fun JsonObject.getTemplate(): JsonObject? {
    return (this.findProperty(KEY_SPEC)?.value as? JsonObject?)
        ?.findProperty(KEY_TEMPLATE)?.value as? JsonObject?
}
fun PsiElement.hasTemplateLabels(): Boolean {
    return this.getTemplateLabels() != null
}

fun PsiElement.getTemplateLabels(): PsiElement? {
    return when(this) {
        is YAMLMapping -> this.getTemplateLabels()
        is JsonObject -> this.getTemplateLabels()
        else ->
            null
    }
}

fun YAMLMapping.getTemplateLabels(): YAMLMapping? {
    return this.getTemplate()?.getLabels()
}

fun JsonObject.getTemplateLabels(): JsonObject? {
    return this.getTemplate()?.getLabels()
}

/**
 * Returns the [PsiElement] named "binaryData".
 * Only [YAMLKeyValue] and [JsonProperty] are supported. Returns `null` otherwise.
 *
 * @return the PsiElement named "binaryData"
 */
fun PsiElement.getBinaryData(): PsiElement? {
    return when (this) {
        is YAMLMapping ->
            this.getKeyValueByKey(KEY_BINARY_DATA)?.value
        is JsonObject ->
            this.findProperty(KEY_BINARY_DATA)?.value
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
fun PsiElement.getDataValue(): PsiElement? {
    return when (this) {
        is YAMLMapping -> this.getKeyValueByKey(KEY_DATA)?.value
        is JsonObject -> this.findProperty(KEY_DATA)?.value
        else -> null
    }
}

