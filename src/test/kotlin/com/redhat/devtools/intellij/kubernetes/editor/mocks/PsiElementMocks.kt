/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNamedElement
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLValue
import org.mockito.MockedStatic
import org.mockito.Mockito

fun createProjectWithServices(
    yamlGenerator: YAMLElementGenerator? = null,
    psiFileFactory: PsiFileFactory? = null
): Project {
    return mock<Project> {
        on { getService(any<Class<*>>()) } doAnswer { invocation ->
            when {
                YAMLElementGenerator::class.java == invocation.getArgument<Class<*>>(0) ->
                    yamlGenerator

                PsiFileFactory::class.java == invocation.getArgument<Class<*>>(0) ->
                    psiFileFactory

                else -> null
            }
        }
    }
}

fun createYAMLMapping(children: List<YAMLKeyValue>): YAMLMapping {
    return mock<YAMLMapping> {
        on { keyValues } doReturn children
        doAnswer { invocation ->
            val requestedKey = invocation.getArgument<String>(0)
            children.find { label -> label.keyText == requestedKey }
        }.whenever(mock).getKeyValueByKey(any())

        on { mock.children } doReturn children.toTypedArray()
        // invoked by PsiElementVisitor
        on { acceptChildren(any()) } doAnswer { invocation ->
            val visitor = invocation.getArgument<PsiElementVisitor>(0)
            children.forEach { it.accept(visitor) }
        }
        children.forEach{ doReturn(mock).whenever(it).parent }
    }
}

fun createYAMLKeyValue(
    key: String,
    value: String? = null,
    parent: YAMLMapping? = null,
    project: Project = mock()
): YAMLKeyValue {
    val valueElement = mock<YAMLValue> {
        on { text } doReturn value
    }
    return createYAMLKeyValue(key, valueElement, parent, project)
}

fun createYAMLKeyValue(key: String, value: YAMLValue, parent: YAMLMapping? = null, project: Project = mock()): YAMLKeyValue {
    val keyElement = mock<PsiNamedElement> {
        on { name } doReturn key
        on { mock.project } doReturn project
    }
    return createYAMLKeyValue(keyElement, value, parent, project)
}

fun createYAMLKeyValue(
    key: PsiNamedElement,
    value: YAMLValue,
    parent: YAMLMapping?,
    project: Project = mock()
): YAMLKeyValue {
    val keyName = key.name!!
    val valueText = value.text
    val keyValue = mock<YAMLKeyValue> {
        on { mock.key } doReturn key
        on { mock.keyText } doReturn keyName
        on { mock.value } doReturn value
        on { mock.valueText } doReturn valueText
        on { mock.parent } doReturn parent
        on { mock.project } doReturn project
        on { accept(any()) } doAnswer { invocation ->
            val visitor = invocation.getArgument<PsiElementVisitor>(0)
            visitor.visitElement(mock)
        }
    }
    whenever(value.parent)
        .thenReturn(keyValue)
    if (parent != null) {
        whenever(parent.getKeyValueByKey(keyName))
            .thenReturn(keyValue)
    }
    return keyValue
}

fun createYAMLMapping(key: String, value: String): YAMLMapping {
    val keyValue = createYAMLKeyValue(key, value)
    return createYAMLMapping(listOf(keyValue))
}

fun createYAMLDocument(yamlValue: YAMLValue): YAMLDocument {
    return mock {
        on { topLevelValue } doReturn yamlValue
    }
}

fun createYAMLSequence(expressions: List<YAMLSequenceItem>): YAMLSequence {
    return mock<YAMLSequence> {
        on { mock.items } doReturn expressions
    }
}

fun createYAMLGenerator(): YAMLElementGenerator {
    return mock<YAMLElementGenerator> {
        on { createYamlKeyValue(any<String>(), any<String>()) } doReturn mock()
    }
}

fun createJsonObject(
    name: String? = null,
    properties: List<JsonProperty> = emptyList(),
    parent: PsiElement? = null,
    project: Project = mock()
): JsonObject {
    return mock {
        on { mock.children } doReturn properties.toTypedArray()
        on { mock.propertyList } doReturn properties
        on { mock.name } doReturn name
        on { mock.parent } doReturn parent
        on { mock.project } doReturn project
    }
}

fun createJsonObject(children: List<JsonProperty>): JsonObject {
    return mock<JsonObject> {
        on { propertyList } doReturn children
        doAnswer { invocation ->
            val requestedKey = invocation.getArgument<String>(0)
            children.find { label -> label.name == requestedKey }
        }.whenever(mock).findProperty(any())

        on { mock.children } doReturn children.toTypedArray()
        // invoked by PsiElementVisitor
        on { acceptChildren(any()) } doAnswer { invocation ->
            val visitor = invocation.getArgument<PsiElementVisitor>(0)
            children.forEach { it.accept(visitor) }
        }
        children.forEach{ doReturn(mock).whenever(it).parent }
    }
}

fun createJsonProperty(
    name: String,
    value: String,
    parent: JsonObject? = null,
    project: Project = mock()
): JsonProperty {
    val valueElement = mock<JsonValue> {
        on { mock.text } doReturn value
    }
    return createJsonProperty(name, valueElement, parent, project)
}

fun createJsonProperty(
    name: String,
    valueElement: JsonValue,
    parent: JsonObject? = null,
    project: Project = mock()
): JsonProperty {
    val nameElement = mock<JsonValue> {
        on { mock.name } doReturn name
    }
    return createJsonProperty(nameElement, valueElement, parent, project)
}

fun createJsonProperty(
    nameElement: JsonValue,
    valueElement: JsonValue,
    parent: JsonObject?,
    project: Project = mock()
): JsonProperty {
    val name = nameElement.name!!
    val property = mock<JsonProperty> {
        on { mock.name } doReturn name
        on { mock.nameElement } doReturn nameElement
        on { mock.value } doReturn valueElement
        on { mock.parent } doReturn parent
        on { mock.project } doReturn project
    }
    whenever(valueElement.parent)
        .thenReturn(property)
    if (parent != null) {
        whenever(parent.findProperty(name))
            .thenReturn(property)
    }
    return property
}

fun createJsonPsiFile(properties: List<JsonProperty>): PsiFile {
    val firstChild: JsonObject = mock {
        on { propertyList } doReturn properties
    }
    return mock {
        on { getFirstChild() } doReturn firstChild
    }
}

fun createJsonPsiFileFactory(properties: List<JsonProperty>): PsiFileFactory {
    val file = createJsonPsiFile(properties)
    return createPsiFileFactory(file)
}

fun createPsiFileFactory(psiFile: PsiFile): PsiFileFactory {
    return mock<PsiFileFactory> {
        on { createFileFromText(any<String>(), any<FileType>(), any<String>()) } doReturn psiFile
    }
}

fun runWithMockKubernetesTypeInfo(type: KubernetesTypeInfo, test: (staticMock: MockedStatic<KubernetesTypeInfo>) -> Unit) {
    Mockito.mockStatic(KubernetesTypeInfo::class.java).use { staticMock ->
        whenever(KubernetesTypeInfo.create(any<PsiElement>()))
            .thenReturn(type)
        test.invoke(staticMock)
    }
}
