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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

fun createLabelsFor(labels: List<YAMLKeyValue>): YAMLMapping {
    return mock<YAMLMapping> {
        on { keyValues } doReturn labels
        doAnswer { invocation ->
            val requestedKey = invocation.getArgument<String>(0)
            labels.find { label -> label.keyText == requestedKey }
        }.whenever(mock).getKeyValueByKey(any())
    }
}

fun createLabelsFor(labels: List<YAMLKeyValue>, parent: YAMLMapping): YAMLKeyValue {
    val labelMappings = createYAMLMapping(labels)
    return createLabelsFor(labelMappings, parent)
}

fun createLabelsFor(labelsChildren: YAMLMapping, parent: YAMLMapping): YAMLKeyValue {
    val metadataChildren = mock<YAMLMapping>()
    createKeyValueFor("metadata", metadataChildren, parent)
    return createKeyValueFor("labels", labelsChildren, metadataChildren)
}

fun createLabelsFor(labelsChildren: JsonObject, parent: JsonObject): JsonProperty {
    val metadataChildren = mock<JsonObject>()
    createPropertyFor("metadata", metadataChildren, parent)
    return createPropertyFor("labels", labelsChildren, metadataChildren)
}


fun createMatchLabelsFor(matchLabels: YAMLMapping, parent: YAMLMapping): YAMLKeyValue {
    val selectorChildren = mock<YAMLMapping>()
    createSelectorFor(selectorChildren, parent)
    return createKeyValueFor("matchLabels", matchLabels, selectorChildren)
}

fun createMatchExpressionsFor(matchExpressionsChildren: YAMLSequence, parent: YAMLMapping): YAMLKeyValue {
    val selectorChildren = mock<YAMLMapping>()
    createSelectorFor(selectorChildren, parent)
    return createKeyValueFor("matchExpressions", matchExpressionsChildren, selectorChildren)
}

fun createYAMLExpression(key: String, operator: String, values: List<String>): YAMLSequenceItem {
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
        on { getKeyValueByKey("key") } doReturn keyElement
        on { getKeyValueByKey("operator") } doReturn operatorElement
        on { getKeyValueByKey("values") } doReturn valuesElement
    }
    return mock<YAMLSequenceItem> {
        on { mock.value } doReturn mapping
    }
}

fun createSelectorFor(selectorChildren: YAMLMapping = mock(), parent: YAMLMapping): YAMLKeyValue {
    val specChildren = mock<YAMLMapping>()
    createKeyValueFor("spec", specChildren, parent)
    return createKeyValueFor("selector", selectorChildren, specChildren)
}

fun createSelectorFor(selectorChildren: JsonObject = mock(), parent: JsonObject): JsonProperty {
    val specChildren = mock<JsonObject>()
    createPropertyFor("spec", specChildren, parent)
    return createPropertyFor("selector", selectorChildren, specChildren)
}

fun createMetadataFor(parent: YAMLMapping): YAMLMapping {
    val metadataChildren = mock<YAMLMapping>()
    createKeyValueFor("metadata", metadataChildren, parent)
    return metadataChildren
}

fun createMetadataFor(parent: JsonObject): JsonObject {
    val metadataChildren = mock<JsonObject>()
    createPropertyFor("metadata", metadataChildren, parent)
    return metadataChildren
}

fun createLabelsFor(labels: List<JsonProperty>, parent: JsonObject): JsonProperty {
    val labelMappings = createJsonObjectFor(properties = labels)
    return createLabelsFor(labelMappings, parent)
}

