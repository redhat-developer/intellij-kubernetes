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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJobTemplate
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonObject
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonProperty
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMetadata
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelector
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createTemplate
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.editor.mocks.runWithMockKubernetesTypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import kotlin.collections.listOf

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
        val metadata = yamlElement.createMetadata()
        val name = createYAMLKeyValue("name", "yoda", metadata)
        // when
        val result = (yamlElement as PsiElement).getResourceName()
        // then
        assertThat(result).isEqualTo(name.value)
    }

    @Test
    fun `#getResourceName for PsiElement Json returns metadata`() {
        // given
        val metadata = jsonElement.createMetadata()
        val name = createJsonProperty("name", "yoda", metadata)
        // when
        val result = (jsonElement as PsiElement).getResourceName()
        // then
        assertThat(result).isEqualTo(name.value)
    }

    @Test
    fun `#getMetadata for Json returns metadata`() {
        // given
        val metadata = jsonElement.createMetadata()
        // when
        val returned = jsonElement.getMetadata()
        // then
        assertThat(returned).isSameAs(metadata)
    }

    @Test
    fun `#getMetadata for Json without metadata returns null`() {
        // given
        whenever(jsonElement.findProperty("metadata"))
            .thenReturn(null)
        // when
        val returned = jsonElement.getMetadata()
        // then
        assertThat(returned).isNull()
    }

    @Test
    fun `#getMetadata for YAML returns metadata`() {
        // given
        val metadataChildren = yamlElement.createMetadata()
        // when
        val returned = yamlElement.getMetadata()
        // then
        assertThat(returned).isSameAs(metadataChildren)
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
    fun `#hasLabels for Yaml returns true if object has labels`() {
        // given
        val label = createYAMLKeyValue("jedi", "leia")
        yamlElement.createLabels(listOf(label))
        // when
        val hasLabels = yamlElement.hasLabels()
        // then
        assertThat(hasLabels).isTrue()
    }

    @Test
    fun `#hasLabels for YAML returns false if object has no labels`() {
        // given
        // when
        val hasLabels = yamlElement.hasLabels()
        // then
        assertThat(hasLabels).isFalse()
    }

    @Test
    fun `#hasLabels for Json returns true if object has labels`() {
        // given
        val label = createJsonProperty("jedi", "yoda")
        jsonElement.createLabels(listOf(label))
        // when
        val hasLabels = jsonElement.hasLabels()
        // then
        assertThat(hasLabels).isTrue()
    }

    @Test
    fun `#hasLabels for Json returns false if object has no labels`() {
        // given
        // when
        val hasLabels = jsonElement.hasLabels()
        // then
        assertThat(hasLabels).isFalse()
    }

    @Test
    fun `#getLabels for Json returns labels object`() {
        // given
        val labelsProperty = jsonElement.createLabels(emptyList())
        // when
        val returned = jsonElement.getLabels()
        // then
        assertThat(returned).isSameAs(labelsProperty.value)
    }

    @Test
    fun `#getLabels for Json returns null if labels dont exist`() {
        // given no labels are created
        // when
        val returned = jsonElement.getLabels()
        // then
        assertThat(returned).isNull()
    }

    @Test
    fun `#getLabels for YAML returns labels`() {
        // given
        val metadata = yamlElement.createMetadata()
        val labelsMapping = mock<YAMLMapping>()
        createYAMLKeyValue("labels", labelsMapping, metadata)
        // when
        val returned = yamlElement.getLabels()
        // then
        assertThat(returned).isSameAs(labelsMapping)
    }

    @Test
    fun `#getLabels returns null if labels dont exist`() {
        // given no labels are created
        // when
        val returned = yamlElement.getLabels()

        // then
        assertThat(returned).isNull()
    }

    @Test
    fun `#hasSelector returns true when selector exists for YAML `() {
        // given
        val selector = yamlElement.createSelector()
        // when
        val hasSelector = yamlElement.hasSelector()
        // then
        assertThat(hasSelector).isTrue()
    }

    @Test
    fun `#getSelector for YAML returns selector`() {
        // given
        val selectorKeyValue = yamlElement.createSelector()
        // when
        val selector = yamlElement.getSelector()
        // then
        assertThat(selector).isSameAs(selectorKeyValue.value)
    }

    @Test
    fun `#getSelector for Json returns selector`() {
        // given
        val selectorKeyValue = jsonElement.createSelector()
        // when
        val selector = jsonElement.getSelector()
        // then
        assertThat(selector).isSameAs(selectorKeyValue.value)
    }

    @Test
    fun `#getKey for YAML returns key of YamlKeyValue`() {
        // given
        val selectorKeyValue = yamlElement.createSelector()
        // when
        val selectorKey = (selectorKeyValue as PsiElement).getKey() // extension method defined for PsiElement
        // then
        assertThat(selectorKey).isSameAs(selectorKeyValue.key)
    }

    @Test
    fun `#getKey for Json returns nameElement of JsonProperty`() {
        // given
        val selectorKeyValue = jsonElement.createSelector()
        // when
        val selectorKey = (selectorKeyValue as PsiElement).getKey() // extension method defined for PsiElement
        // then
        assertThat(selectorKey).isSameAs(selectorKeyValue.nameElement)
    }

    @Test
    fun `#getTemplate for YAML returns template mapping`() {
        // given
        val specMapping = mock<YAMLMapping>()
        createYAMLKeyValue("spec", specMapping, yamlElement)
        val templateMapping = mock<YAMLMapping>()
        createYAMLKeyValue("template", templateMapping, specMapping)
        // when
        val result = yamlElement.getTemplate()
        // then
        assertThat(result).isSameAs(templateMapping)
    }

    @Test
    fun `#getJobTemplate for YAML returns jobTemplate mapping`() {
        // given
        val specMapping = mock<YAMLMapping>()
        createYAMLKeyValue("spec", specMapping, yamlElement)
        val templateMapping = mock<YAMLMapping>()
        createYAMLKeyValue("jobTemplate", templateMapping, specMapping)
        // when
        val result = yamlElement.getJobTemplate()
        // then
        assertThat(result).isSameAs(templateMapping)
    }

    @Test
    fun `#getTemplateLabel for YAML returns template labels`() {
        // given
        val templateLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "skywalker")
        ))
        yamlElement.createTemplate(
            createYAMLMapping(listOf(
                createYAMLKeyValue(
                    "metadata",
                    createYAMLMapping(listOf(
                        createYAMLKeyValue(
                            "labels",
                            templateLabels
                        )
                    ))
                )
            ))
        )
        // when
        val found = yamlElement.getTemplateLabels()
        // then
        assertThat(found).isSameAs(templateLabels)
    }

    @Test
    fun `#getJobTemplateLabel for YAML returns template labels`() {
        // given
        val jobTemplateLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "skywalker")
        ))
        yamlElement.createJobTemplate(
            createYAMLMapping(listOf(
                createYAMLKeyValue("spec",
                    createYAMLMapping(listOf(
                        createYAMLKeyValue("template",
                            createYAMLMapping(listOf(
                                createYAMLKeyValue(
                                    "metadata",
                                    createYAMLMapping(listOf(
                                        createYAMLKeyValue(
                                            "labels",
                                            jobTemplateLabels
                                        )
                                    ))
                                )
                            ))
                        )
                    ))
                )
            ))
        )
        // when
        val found = yamlElement.getJobTemplate()?.getTemplateLabels()
        // then
        assertThat(found).isSameAs(jobTemplateLabels)
    }

    @Test
    fun `#getTemplateLabel for Json returns template labels`() {
        // given
        val jobTemplateLabels = createJsonObject(listOf(
            createJsonProperty("jedi", "skywalker")
        ))
        jsonElement.createTemplate(
            createJsonObject(listOf(
                createJsonProperty(
                    "metadata",
                    createJsonObject(listOf(
                        createJsonProperty(
                            "labels",
                            jobTemplateLabels
                        )
                    ))
                )
            ))
        )
        // when
        val found = jsonElement.getTemplateLabels()
        // then
        assertThat(found).isSameAs(jobTemplateLabels)
    }

    @Test
    fun `#getJobTemplateLabel for Json returns template labels`() {
        // given
        val templateLabels = createJsonObject(listOf(
            createJsonProperty("jedi", "skywalker")
        ))
        jsonElement.createJobTemplate(
            createJsonObject(listOf(
                createJsonProperty("spec",
                    createJsonObject(listOf(
                        createJsonProperty("template",
                            createJsonObject(listOf(
                                createJsonProperty(
                                    "metadata",
                                    createJsonObject(listOf(
                                        createJsonProperty(
                                            "labels",
                                            templateLabels
                                        )
                                    ))
                                )
                            ))
                        )
                    ))
                )
            ))
        )
        // when
        val found = jsonElement.getJobTemplate()?.getTemplateLabels()
        // then
        assertThat(found).isSameAs(templateLabels)
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
        runWithMockKubernetesTypeInfo(mock<KubernetesTypeInfo>()) { staticMock: MockedStatic<KubernetesTypeInfo> ->
            // when
            yamlFile.isKubernetesResource()
            // then
            staticMock.verify({ KubernetesTypeInfo.create(yamlFile) }, never())
        }
    }

    @Test
    fun `#isKubernetesResource for YAMLMapping creates KubernetesTypeInfo`() {
        // given
        val yamlMapping = mock<YAMLMapping>()
        runWithMockKubernetesTypeInfo(mock<KubernetesTypeInfo>()) { staticMock: MockedStatic<KubernetesTypeInfo> ->
            // when
            yamlMapping.isKubernetesResource()
            // then
            staticMock.verify { KubernetesTypeInfo.create(yamlMapping) }
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
        runWithMockKubernetesTypeInfo(mock<KubernetesTypeInfo>()) { staticMock: MockedStatic<KubernetesTypeInfo> ->
            // when
            yamlMapping.getKubernetesTypeInfo()
            // then
            staticMock.verify { KubernetesTypeInfo.create(yamlMapping) }
        }
    }

    @Test
    fun `#getBinaryData should return YAMLKeyValue named binaryData`() {
        // given
        createYAMLKeyValue("binaryData", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getBinaryData()
        // then
        assertThat(found).isNotNull()
    }

    @Test
    fun `#getBinaryData should return null if there is no child named binaryData`() {
        // given
        createYAMLKeyValue("anakin", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getBinaryData()
        // then
        assertThat(found).isNull()
    }

    @Test
    fun `#getDataValue should return YAMLKeyValue named data`() {
        // given
        val data = createYAMLKeyValue("data", "yoda", yamlElement)
        // when
        val found = yamlElement.getDataValue()
        // then
        assertThat(found).isEqualTo(data.value)
    }

    @Test
    fun `#getDataValue should return null if there is no child named data`() {
        // given
        createYAMLKeyValue("yoda", mock<YAMLMapping>(), yamlElement)
        // when
        val found = yamlElement.getDataValue()
        // then
        assertThat(found).isNull()
    }

    @Test
    fun `#getDataValue should return JsonProperty named data`() {
        // given
        createJsonProperty("data", value = "anakin", jsonElement)
        // when
        val found = jsonElement.getDataValue()
        // then
        assertThat(found?.text).isEqualTo("anakin")
    }

}
