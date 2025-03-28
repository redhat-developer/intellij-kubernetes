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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMetadataFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createProperty
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelectorFor
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class ResourcePsiElementUtilsTest {

    private lateinit var yamlElement: YAMLMapping
    private lateinit var jsonElement: JsonObject

    @Before
    fun before() {
        this.yamlElement = mock()
        this.jsonElement = mock()
    }

    @Test
    fun `#getAllElements for YAMLFile returns topLevelValues`() {
        // given
        val yamlFile = mock<YAMLFile>()
        val yamlDocument = mock<YAMLDocument>()
        whenever(yamlFile.documents)
            .thenReturn(listOf(yamlDocument))
        val topLevelValue = mock<YAMLMapping>()
        whenever(yamlDocument.topLevelValue)
            .thenReturn(topLevelValue)
        // when
        val result = yamlFile.getAllElements()
        // then
        assertThat(result).containsExactly(topLevelValue)
    }

    @Test
    fun `#getAllElements for JsonFile returns allTopLevelValues`() {
        // given
        val jsonFile = mock<JsonFile>()
        val topLevelValue = mock<JsonObject>()
        whenever(jsonFile.allTopLevelValues)
            .thenReturn(listOf(topLevelValue))
        // when
        val result = jsonFile.getAllElements()
        // then
        assertThat(result).containsExactly(topLevelValue)
    }

    @Test
    fun `#getAllElements for unknown file returns null`() {
        // given
        val xmlFile = mock<XmlFile>()
        // when
        val result = xmlFile.getAllElements()
        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `#getResourceName for PsiElement YAML returns metadata`() {
        // given
        val metadata = createMetadataFor(yamlElement)
        val name = createKeyValue("name", "yoda", metadata)
        // when
        val result = (yamlElement as PsiElement).getResourceName()
        // then
        assertThat(result).isEqualTo(name.value)
    }

    @Test
    fun `#getResourceName for PsiElement Json returns metadata`() {
        // given
        val metadata = createMetadataFor(jsonElement)
        val name = createProperty("name", "yoda", metadata)
        // when
        val result = (jsonElement as PsiElement).getResourceName()
        // then
        assertThat(result).isEqualTo(name.value)
    }

    @Test
    fun `#getMetadata for Json returns metadata`() {
        // given
        val metadataValue = createMetadataFor(jsonElement)
        // when
        val result = jsonElement.getMetadata()
        // then
        assertThat(result).isSameAs(metadataValue)
    }

    @Test
    fun `#getMetadata for Json without metadata returns null`() {
        // given
        whenever(jsonElement.findProperty("metadata"))
            .thenReturn(null)
        // when
        val result = jsonElement.getMetadata()
        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#getMetadata for YAML returns metadata`() {
        // given
        val metadataChildren = createMetadataFor(yamlElement)
        // when
        val result = yamlElement.getMetadata()
        // then
        assertThat(result).isSameAs(metadataChildren)
    }

    @Test
    fun `#getMetadata for YAML without metadata returns null`() {
        // given
        whenever(yamlElement.getKeyValueByKey("metadata"))
            .thenReturn(null)
        // when
        val result = yamlElement.getMetadata()
        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#getLabels for Json returns labels object`() {
        // given
        val metadata = createMetadataFor(jsonElement)
        val labelsMapping = mock<JsonObject>()
        createProperty("labels", labelsMapping, metadata)
        // when
        val result = jsonElement.getLabels()
        // then
        assertThat(result).isSameAs(labelsMapping)
    }

    @Test
    fun `#getLabels for YAML returns labels`() {
        // given
        val metadata = createMetadataFor(yamlElement)
        val labelsMapping = mock<YAMLMapping>()
        createKeyValue("labels", labelsMapping, metadata)
        // when
        val result = yamlElement.getLabels()
        // then
        assertThat(result).isSameAs(labelsMapping)
    }

    @Test
    fun `#hasSelector returns true when selector exists for YAML `() {
        // given
        val selector = createSelectorFor(parent = yamlElement)
        // when
        val result = yamlElement.hasSelector()
        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `#getSelector for YAML returns selector`() {
        // given
        val selector = createSelectorFor(parent = yamlElement)
        // when
        val result = yamlElement.getSelector()
        // then
        assertThat(result).isSameAs(selector.value)
    }

    @Test
    fun `#getSelector for Json returns selector`() {
        // given
        val selector = createSelectorFor(parent = jsonElement)
        // when
        val result = jsonElement.getSelector()
        // then
        assertThat(result).isSameAs(selector.value)
    }

    @Test
    fun `#getSelectorKeyElement for YAML returns key of parent element`() {
        // given
        val selector = createSelectorFor(parent = yamlElement)
        // when
        val result = yamlElement.getSelectorKeyElement()
        // then
        assertThat(result).isSameAs(selector.key)
    }

    @Test
    fun `#getSelectorKeyElement for Json returns key of parent element`() {
        // given
        val selector = createSelectorFor(parent = jsonElement)
        // when
        val result = jsonElement.getSelectorKeyElement()
        // then
        assertThat(result).isSameAs(selector.nameElement)
    }

    @Test
    fun `#getTemplate for YAML returns template mapping`() {
        // given
        val specMapping = mock<YAMLMapping>()
        createKeyValue("spec", specMapping, yamlElement)
        val templateMapping = mock<YAMLMapping>()
        createKeyValue("template", templateMapping, specMapping)
        // when
        val result = yamlElement.getTemplate()
        // then
        assertThat(result).isSameAs(templateMapping)
    }

    @Test
    fun `#isKubernetesResource for PsiFile returns false`() {
        // given
        val psiFile = mock<PsiFile>()
        // when
        val result = psiFile.isKubernetesResource()
        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `#isKubernetesResource for YAMLFile does NOT create KubernetesTypeInfo`() {
        // given
        val yamlFile = mock<YAMLFile>()
        val createKubernetesTypeInfo = Mockito.mockStatic(KubernetesTypeInfo::class.java)
        createKubernetesTypeInfo.use { createKubernetesTypeInfo ->
            createKubernetesTypeInfo
                .`when`<Any> { KubernetesTypeInfo.create(any<PsiElement>()) }
                .thenReturn(mock<KubernetesTypeInfo>())
            // when
            yamlFile.isKubernetesResource()
            // then
            createKubernetesTypeInfo.verify({ KubernetesTypeInfo.create(yamlFile) }, never())
        }
    }

    @Test
    fun `#isKubernetesResource for YAMLMapping creates KubernetesTypeInfo`() {
        // given
        val yamlMapping = mock<YAMLMapping>()
        val createKubernetesTypeInfo = Mockito.mockStatic(KubernetesTypeInfo::class.java)
        createKubernetesTypeInfo.use { createKubernetesTypeInfo ->
            createKubernetesTypeInfo
                .`when`<Any> { KubernetesTypeInfo.create(any<PsiElement>()) }
                .thenReturn(mock<KubernetesTypeInfo>())
            // when
            yamlMapping.isKubernetesResource()
            // then
            createKubernetesTypeInfo.verify { KubernetesTypeInfo.create(yamlMapping) }
        }
    }

    @Test
    fun `#getKubernetesTypeInfo for PsiFile returns null`() {
        // given
        val psiFile = mock<PsiFile>()
        // when
        val result = psiFile.getKubernetesTypeInfo()
        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#getKubernetesTypeInfo for YAMLMapping creates KubernetesTypeInfo`() {
        // given
        val yamlMapping = mock<YAMLMapping>()
        val createKubernetesTypeInfo = Mockito.mockStatic(KubernetesTypeInfo::class.java)
        createKubernetesTypeInfo.use { createKubernetesTypeInfo ->
            createKubernetesTypeInfo
                .`when`<Any> { KubernetesTypeInfo.create(any<PsiElement>()) }
                .thenReturn(mock<KubernetesTypeInfo>())
            // when
            yamlMapping.getKubernetesTypeInfo()
            // then
            createKubernetesTypeInfo.verify { KubernetesTypeInfo.create(yamlMapping) }
        }
    }

    @Test
    fun `#getBinaryData should return YAMLKeyValue named binaryData`() {
        // given
        val data = createKeyValue("binaryData", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getBinaryData()
        // then
        assertThat(found).isNotNull()
    }

    @Test
    fun `#getBinaryData should return null if there is no child named binaryData`() {
        // given
        val data = createKeyValue("anakin", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getBinaryData()
        // then
        assertThat(found).isNull()
    }

    @Test
    fun `#getDataValue should return YAMLKeyValue named data`() {
        // given
        val data = createKeyValue("data", "yoda", yamlElement)
        // when
        val found = yamlElement.getDataValue()
        // then
        assertThat(found).isEqualTo(data.value)
    }

    @Test
    fun `#getDataValue should return null if there is no child named data`() {
        // given
        val yoda = createKeyValue("yoda", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getDataValue()
        // then
        assertThat(found).isNull()
    }

    @Test
    fun `#getDataValue should return JsonProperty named data`() {
        // given
        val data = createProperty("data", value = "anakin", jsonElement)
        // when
        val found = jsonElement.getDataValue()
        // then
        assertThat(found?.text).isEqualTo("anakin")
    }

}
