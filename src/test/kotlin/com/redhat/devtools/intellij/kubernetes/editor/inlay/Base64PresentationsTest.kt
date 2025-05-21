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
package com.redhat.devtools.intellij.kubernetes.editor.inlay

import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.inlay.base64.Base64Presentations
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.kubernetesTypeInfo
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test


class Base64PresentationsTest {

    private lateinit var secret: KubernetesTypeInfo
    private lateinit var configMap: KubernetesTypeInfo
    private lateinit var pod: KubernetesTypeInfo

    private lateinit var yamlElement: YAMLMapping
    private lateinit var stringPresentation:
                (element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit
    private lateinit var binaryPresentation:
                (element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit

    @Before
    fun before() {
        this.secret = kubernetesTypeInfo("Secret", "v1")
        this.configMap = kubernetesTypeInfo("ConfigMap", "v1")
        this.pod = kubernetesTypeInfo("Pod", "v1")
        this.yamlElement = mock<YAMLMapping>()
        this.stringPresentation =
            mock<(element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit>()
        this.binaryPresentation =
            mock<(element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit>()
    }

    @Test
    fun `#create invokes string presentation factory if secret has data`() {
        // given
        val dataMapping = createYAMLMapping(
            listOf(
                createYAMLKeyValue("token-id", "NWVtaXRq"),
                createYAMLKeyValue("token-secret", "a3E0Z2lodnN6emduMXAwcg==")
            )
        )
        createYAMLKeyValue("data", dataMapping, yamlElement)
        // when
        Base64Presentations.create(yamlElement, secret, mock(), mock(), mock(), stringPresentation)
        // then
        verify(stringPresentation).invoke(any(), any(), any(), any())
    }

    @Test
    fun `#create does NOT invoke string presentation factory if secret has NO data`() {
        // given
        // when
        Base64Presentations.create(yamlElement, secret, mock(), mock(), mock(), stringPresentation)
        // then
        verify(stringPresentation, never()).invoke(any(), any(), any(), any())
    }

    @Test
    fun `#create invokes binary presentation factory if ConfigMap has binaryData`() {
        // given
        val binaryDataMapping = createYAMLMapping(
            listOf(
                createYAMLKeyValue("my-binary-file.bin", "U29tZSBiYXNlNjQgZW5jb2RlZCBiaW5hcnkgZGF0YQ"),
                createYAMLKeyValue("another-binary.dat", "VGhpcyBpcyBhbm90aGVyIGV4YW1wbGUgb2YgYmluYXJ5IGRhdGE")
            )
        )
        createYAMLKeyValue("binaryData", binaryDataMapping, yamlElement)
        // when
        Base64Presentations.create(yamlElement, configMap, mock(), mock(), mock(), mock(), binaryPresentation)
        // then
        verify(binaryPresentation).invoke(any(), any(), any(), any())
    }


    @Test
    fun `#create does NOT invoke binary presentation factory if ConfigMap has NO binaryData`() {
        // given
        // when
        Base64Presentations.create(
            yamlElement, configMap, mock(), mock(), mock(), mock(), binaryPresentation
        )
        // then
        verify(binaryPresentation, never()).invoke(any(), any(), any(), any())
    }

    @Test
    fun `#create should NOT create factory for Pod`() {
        // given
        val binaryDataMapping = createYAMLMapping(
            listOf(
                createYAMLKeyValue("my-binary-file.bin", "U29tZSBiYXNlNjQgZW5jb2RlZCBiaW5hcnkgZGF0YQ"),
                createYAMLKeyValue("another-binary.dat", "VGhpcyBpcyBhbm90aGVyIGV4YW1wbGUgb2YgYmluYXJ5IGRhdGE")
            )
        )
        createYAMLKeyValue("binaryData", binaryDataMapping, yamlElement)
        val dataMapping = createYAMLMapping(
            listOf(
                createYAMLKeyValue("token-id", "NWVtaXRq"),
                createYAMLKeyValue("token-secret", "a3E0Z2lodnN6emduMXAwcg==")
            )
        )
        createYAMLKeyValue("data", dataMapping, yamlElement)
        // when
        Base64Presentations.create(
            mock(), pod, mock(), mock(), mock(), stringPresentation, binaryPresentation
        )
        // then
        verify(stringPresentation, never()).invoke(any(), any(), any(), any())
        verify(binaryPresentation, never()).invoke(any(), any(), any(), any())
    }
}
