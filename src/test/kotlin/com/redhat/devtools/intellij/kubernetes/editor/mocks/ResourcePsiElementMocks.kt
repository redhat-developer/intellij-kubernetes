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
package com.redhat.devtools.intellij.kubernetes.editor.mocks

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelector
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

fun YAMLMapping.createLabels(labels: List<YAMLKeyValue>): YAMLKeyValue {
    val labelMappings = createYAMLMapping(labels)
    return this.createLabels(labelMappings)
}

private const val KEY_LABELS = "labels"
private const val KEY_MATCHLABELS = "matchLabels"
private const val KEY_MATCHEXPRESSIONS = "matchExpressions"
private const val KEY_MATCHEXPRESSIONS_KEY = "key"
private const val KEY_MATCHEXPRESSIONS_OPERATOR = "operator"
private const val KEY_MATCHEXPRESSIONS_VALUES = "values"
private const val KEY_METADATA = "metadata"
private const val KEY_SELECTOR = "selector"
private const val KEY_SPEC = "spec"
private const val KEY_TEMPLATE = "template"

fun YAMLMapping.createLabels(labelsChildren: YAMLMapping): YAMLKeyValue {
    val metadataChildren = mock<YAMLMapping>()
    createYAMLKeyValue(KEY_METADATA, metadataChildren, this)
    return createYAMLKeyValue(KEY_LABELS, labelsChildren, metadataChildren)
}

fun JsonObject.createLabels(labels: List<JsonProperty>): JsonProperty {
    val labelMappings = createJsonObject(properties = labels)
    return this.createLabels(labelMappings)
}

fun JsonObject.createLabels(labelsChildren: JsonObject): JsonProperty {
    val metadataChildren = mock<JsonObject>()
    createJsonProperty(KEY_METADATA, metadataChildren, this)
    return createJsonProperty(KEY_LABELS, labelsChildren, metadataChildren)
}

fun YAMLMapping.createTemplate(templateChildren: YAMLMapping): YAMLKeyValue {
    var spec = getKeyValueByKey(KEY_SPEC)
    if (spec == null) {
        val specMapping = mock<YAMLMapping>()
        spec = createYAMLKeyValue(KEY_SPEC, specMapping, this)
    }
    return createYAMLKeyValue(KEY_TEMPLATE, templateChildren, spec.value as YAMLMapping)
}

fun JsonObject.createTemplate(templateChildren: JsonObject): JsonProperty {
    var spec = findProperty(KEY_SPEC)
    if (spec == null) {
        val specMapping = mock<JsonObject>()
        spec = createJsonProperty(KEY_SPEC, specMapping, this)
    }
    return createJsonProperty(KEY_TEMPLATE, templateChildren, spec.value as JsonObject)
}

fun YAMLMapping.createMatchLabels(matchLabels: YAMLMapping): YAMLKeyValue {
    var selectorChildren = getSelector()
    if (selectorChildren == null) {
        selectorChildren = mock<YAMLMapping>()
        this.createSelector(selectorChildren)
    }
    return createYAMLKeyValue(KEY_MATCHLABELS, matchLabels, selectorChildren)
}

fun YAMLMapping.createMatchExpressions(matchExpressionsChildren: YAMLSequence): YAMLKeyValue {
    var selectorChildren = getSelector()
    if (selectorChildren == null) {
        selectorChildren = mock<YAMLMapping>()
        this.createSelector(selectorChildren)
    }
    return createYAMLKeyValue(KEY_MATCHEXPRESSIONS, matchExpressionsChildren, selectorChildren)
}

fun createYAMLSequenceItem(key: String, operator: String, values: List<String>): YAMLSequenceItem {
    val keyElement = mock<YAMLKeyValue> {
        on { valueText } doReturn key
    }
    val operatorElement = mock<YAMLKeyValue> {
        on { valueText } doReturn operator
    }
    val valueItems = values.map { value ->
        mock<YAMLSequenceItem> {
            on { text } doReturn value
        }
    }
    val valuesSequence = mock<YAMLSequence> {
        on { items } doReturn valueItems
    }
    val valuesElement = mock<YAMLKeyValue> {
        on { value } doReturn valuesSequence
    }

    val mapping = mock<YAMLMapping> {
        on { getKeyValueByKey(KEY_MATCHEXPRESSIONS_KEY) } doReturn keyElement
        on { getKeyValueByKey(KEY_MATCHEXPRESSIONS_OPERATOR) } doReturn operatorElement
        on { getKeyValueByKey(KEY_MATCHEXPRESSIONS_VALUES) } doReturn valuesElement
    }
    return mock<YAMLSequenceItem> {
        on { mock.value } doReturn mapping
    }
}

fun YAMLMapping.createSelector(selectorChildren: YAMLMapping = mock()): YAMLKeyValue {
    var spec = getKeyValueByKey(KEY_SPEC)
    if (spec == null) {
        val specChildren = mock<YAMLMapping>()
        spec = createYAMLKeyValue(KEY_SPEC, specChildren, this)
    }
    return createYAMLKeyValue(KEY_SELECTOR, selectorChildren, spec.value as YAMLMapping)
}

fun JsonObject.createSelector(selectorChildren: JsonObject = mock()): JsonProperty {
    var spec = findProperty(KEY_SPEC)
    if (spec == null) {
        val specChildren = mock<JsonObject>()
        spec = createJsonProperty(KEY_SPEC, specChildren, this)
    }
    return createJsonProperty(KEY_SELECTOR, selectorChildren, spec.value as JsonObject)
}

fun YAMLMapping.createMetadata(): YAMLMapping {
    val metadataChildren = mock<YAMLMapping>()
    createYAMLKeyValue(KEY_METADATA, metadataChildren, this)
    return metadataChildren
}

fun JsonObject.createMetadata(): JsonObject {
    val metadataChildren = mock<JsonObject>()
    createJsonProperty(KEY_METADATA, metadataChildren, this)
    return metadataChildren
}