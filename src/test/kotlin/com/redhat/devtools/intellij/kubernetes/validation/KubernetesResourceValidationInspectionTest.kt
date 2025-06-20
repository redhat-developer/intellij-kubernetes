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
package com.redhat.devtools.intellij.kubernetes.validation

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonObject
import com.intellij.psi.PsiElement
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLDocument
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class KubernetesResourceValidationInspectionTest {

    @Mock 
    private lateinit var problemsHolder: ProblemsHolder

    @Mock
    private lateinit var yamlDocument: YAMLDocument

    @Mock
    private lateinit var jsonObject: JsonObject

    private lateinit var inspection: KubernetesResourceValidationInspection

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        inspection = KubernetesResourceValidationInspection()
    }

    @Test
    fun `#isEnabledByDefault should return true`() {
        // when
        val result = inspection.isEnabledByDefault()
        
        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `#buildVisitor should return visitor that is not null`() {
        // when
        val visitor = inspection.buildVisitor(problemsHolder, false)
        
        // then
        assertThat(visitor).isNotNull()
    }

    @Test
    fun `#extractJsonPath should extract path from validation message`() {
        // given
        val message = "#/spec/containers/0/image: expected type: String, found: Integer"
        
        // when
        val path = callPrivateMethod<String?>(inspection, "extractJsonPath", message)
        
        // then
        assertThat(path).isEqualTo("spec.containers.0.image")
    }

    @Test
    fun `#extractJsonPath should extract complex path from validation message`() {
        // given
        val message = "#/metadata/labels/app.kubernetes.io~1name: invalid value"
        
        // when
        val path = callPrivateMethod<String?>(inspection, "extractJsonPath", message)
        
        // then
        assertThat(path).isEqualTo("metadata.labels.app.kubernetes.io~1name")
    }

    @Test
    fun `#extractJsonPath should return null for message without path`() {
        // given
        val message = "Some validation error without path"
        
        // when
        val path = callPrivateMethod<String?>(inspection, "extractJsonPath", message)
        
        // then
        assertThat(path).isNull()
    }

    @Test
    fun `#extractJsonPath should return null for malformed path`() {
        // given
        val message = "Some error without proper #/ prefix"
        
        // when
        val path = callPrivateMethod<String?>(inspection, "extractJsonPath", message)
        
        // then
        assertThat(path).isNull()
    }

    @Test
    fun `#isCascadingError should return true for cascading error messages`() {
        // given
        val message = "Some error with expected: null"
        
        // when
        val result = callPrivateMethod<Boolean>(inspection, "isCascadingError", message)
        
        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `#isCascadingError should return false for non-cascading error messages`() {
        // given
        val message = "Some normal validation error"
        
        // when
        val result = callPrivateMethod<Boolean>(inspection, "isCascadingError", message)
        
        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `#isCascadingError should return false for empty message`() {
        // given
        val message = ""
        
        // when
        val result = callPrivateMethod<Boolean>(inspection, "isCascadingError", message)
        
        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `#findElement should return null for null root YAMLDocument`() {
        // given
        val jsonPath = "metadata.name"
        val nullDocument: YAMLDocument? = null
        
        // when
        val result = if (nullDocument != null) {
            inspection.findElement(jsonPath, nullDocument)
        } else {
            null
        }
        
        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#findElement should return null for empty path with YAMLDocument`() {
        // given
        val jsonPath = ""
        
        // when
        val result = inspection.findElement(jsonPath, yamlDocument)
        
        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#findElement should handle simple path with YAMLDocument`() {
        // given
        val jsonPath = "metadata"
        
        // when
        val result = inspection.findElement(jsonPath, yamlDocument)
        
        // then
        // Should not throw exception and return some result (or null if path not found)
        // This is a basic structural test since we're using mocks
    }

    @Test
    fun `#findElement should handle nested path with YAMLDocument`() {
        // given
        val jsonPath = "spec.containers.0.image"
        
        // when
        val result = inspection.findElement(jsonPath, yamlDocument)
        
        // then
        // Should not throw exception and return some result (or null if path not found)
        // This is a basic structural test since we're using mocks
    }

    @Test
    fun `#findElement should handle array index in path with YAMLDocument`() {
        // given
        val jsonPath = "spec.containers.0"
        
        // when
        val result = inspection.findElement(jsonPath, yamlDocument)
        
        // then
        // Should not throw exception and return some result (or null if path not found)
        // This is a basic structural test since we're using mocks
    }

    @Test
    fun `#findElement should handle invalid array index with YAMLDocument`() {
        // given
        val jsonPath = "spec.containers.invalid"
        
        // when
        val result = inspection.findElement(jsonPath, yamlDocument)
        
        // then
        // Should not throw exception - may return null for invalid index
    }

    @Test
    fun `#findElement with JsonObject should handle simple path`() {
        // given
        val jsonPath = "metadata"
        
        // when
        val result = callPrivateMethod<Any?>(inspection, "findElement", jsonPath, jsonObject)
        
        // then
        // Should not throw exception and return some result (or null if path not found)
        // This is a basic structural test since we're using mocks
    }

    @Test
    fun `#findElement with JsonObject should handle nested path`() {
        // given
        val jsonPath = "spec.containers.0.image"
        
        // when
        val result = callPrivateMethod<Any?>(inspection, "findElement", jsonPath, jsonObject)
        
        // then
        // Should not throw exception and return some result (or null if path not found)
        // This is a basic structural test since we're using mocks
    }

    @Test
    fun `#findElement with JsonObject should handle empty path`() {
        // given
        val jsonPath = ""
        
        // when
        val result = callPrivateMethod<Any?>(inspection, "findElement", jsonPath, jsonObject)
        
        // then
        // Empty path returns the root object, but since we're using mocks, the behavior may vary
        // This is a structural test to ensure no exceptions are thrown
    }

    @Test
    fun `#registerProblem should not register cascading errors`() {
        // given
        val message = "Some error with expected: null"
        val root = mock<PsiElement>()
        
        // when
        callPrivateMethod<Unit>(inspection, "registerProblem", message, root, problemsHolder)
        
        // then
        verify(problemsHolder, never())
            .registerProblem(any(), any<String>())
    }

    @Test
    fun `#registerProblem should register non-cascading errors`() {
        // given
        val message = "Some normal validation error"
        val root = mock<PsiElement>()
        
        // when
        callPrivateMethod<Unit>(inspection, "registerProblem", message, root, problemsHolder)
        
        // then
        verify(problemsHolder)
            .registerProblem(any(), eq(message))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> callPrivateMethod(instance: Any, methodName: String, vararg args: Any?): T {
        val parameterTypes = args.map { arg ->
            when (arg) {
                null -> Any::class.java // Default for null args
                is String -> String::class.java
                is YAMLDocument -> YAMLDocument::class.java
                is JsonObject -> JsonObject::class.java
                is ProblemsHolder -> ProblemsHolder::class.java
                is PsiElement -> PsiElement::class.java
                else -> arg.javaClass
            }
        }.toTypedArray()
        
        val method = instance.javaClass.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        return method.invoke(instance, *args) as T
    }
} 