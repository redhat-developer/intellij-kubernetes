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
package com.redhat.devtools.intellij.kubernetes.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class KubernetesSchemaCompletionsTest {

    private lateinit var resourceElement: JsonObject

    private lateinit var completionResultSet: CompletionResultSet

    private lateinit var kindProperty: JsonProperty
    private lateinit var kindValue: JsonStringLiteral

    private lateinit var apiVersionProperty: JsonProperty
    private lateinit var apiVersionValue: JsonStringLiteral

    @Before
    fun setUp() {
        this.kindValue = mock {
            on { value } doReturn "Pod"
            on { text } doReturn "\"Pod\""
        }
        this.kindProperty = mock {
            on { value } doReturn kindValue
        }

        this.apiVersionValue = mock {
            on { value } doReturn "v1"
            on { text } doReturn "\"v1\""
        }
        this.apiVersionProperty = mock {
            on { value } doReturn apiVersionValue
        }

        this.resourceElement = mock {
            on { findProperty("kind") } doReturn kindProperty
            on { findProperty("apiVersion") } doReturn apiVersionProperty
        }

        this.completionResultSet = mock<CompletionResultSet>()
    }

    @Test
    fun `#addCompletions adds completions for root level properties`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "apiVersion": {
                    "type": "string",
                    "description": "API version"
                },
                "kind": {
                    "type": "string",
                    "description": "Resource kind"
                },
                "metadata": {
                    "type": "object",
                    "description": "Resource metadata"
                },
                "spec": {
                    "type": "object",
                    "description": "Resource specification"
                }
            }
        }
        """.trimIndent()
        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()
        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement, 
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.firstValue
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "apiVersion", "kind", "metadata", "spec"
        )
    }

    @Test
    fun `#addCompletions adds completions for nested properties`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "spec": {
                    "type": "object",
                    "properties": {
                        "containers": {
                            "type": "array",
                            "description": "List of containers"
                        },
                        "restartPolicy": {
                            "type": "string",
                            "description": "Restart policy"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement, 
            listOf("spec"),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should be called twice: once for keys and once for values (but values will be empty)
        verify(completionResultSet, times(2))
            .addAllElements(lookupElementCaptor.capture())
        
        // Get key completions from the first call
        val capturedElements = lookupElementCaptor.firstValue
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "containers", "restartPolicy"
        )
    }

    @Test
    fun `#addCompletions does NOT adds completions when schema is not found`() {
        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> null }
        )

        // then
        verify(completionResultSet, never())
            .addAllElements(any<Collection<LookupElement>>())
    }

    @Test
    fun `#addCompletions does NOT add completions when kind is missing`() {
        // given
        doReturn(null)
            .whenever(resourceElement).findProperty("kind")

        // when
        KubernetesSchemaCompletions.addCompletions(resourceElement, emptyList(), completionResultSet)

        // then
        verify(completionResultSet, never())
            .addAllElements(any<Collection<LookupElement>>())
    }

    @Test
    fun `#addCompletions does NOT add completions when apiVersion is missing`() {
        // given
        doReturn(null)
            .whenever(resourceElement).findProperty("apiVersion")

        // when
        KubernetesSchemaCompletions.addCompletions(resourceElement, emptyList(), completionResultSet)

        // then
        verify(completionResultSet, never()).addAllElements(any<Collection<LookupElement>>())
    }

    @Test
    fun `#addCompletions adds enum values`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "spec": {
                    "type": "object",
                    "properties": {
                        "restartPolicy": {
                            "type": "string",
                            "enum": ["Always", "OnFailure", "Never"],
                            "description": "Restart policy"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            listOf("spec", "restartPolicy"),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should be called twice: once for keys (empty) and once for enum values
        verify(completionResultSet, times(2))
            .addAllElements(lookupElementCaptor.capture())
        
        // Get enum values from the second call (value completions)
        val capturedElements = lookupElementCaptor.allValues.flatMap { it }
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "Always", "OnFailure", "Never"
        )
    }

    @Test
    fun `#addCompletions adds keys of parent path when current element doesn't comply with schema`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "spec": {
                    "type": "object",
                    "properties": {
                        "containers": {
                            "type": "array",
                            "description": "List of containers"
                        },
                        "volumes": {
                            "type": "array",
                            "description": "List of volumes"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when - typing "invalidProperty" under "spec" should still provide spec-level completions
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            listOf("spec", "invalidProperty"),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should fallback to "spec" level and provide spec properties
        verify(completionResultSet, times(2))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.allValues.flatMap { it }
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "containers", "volumes"
        )
    }

    @Test
    fun `#addCompletions adds kes of root level for completely invalid paths`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "apiVersion": {
                    "type": "string",
                    "description": "API version"
                },
                "kind": {
                    "type": "string",
                    "description": "Resource kind"
                },
                "metadata": {
                    "type": "object",
                    "description": "Resource metadata"
                },
                "spec": {
                    "type": "object",
                    "description": "Resource specification"
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when - completely invalid path should fall back to root
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            listOf("completely", "invalid", "path", "that", "does", "not", "exist"),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should fallback to root level and provide root properties
        verify(completionResultSet, times(2))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.allValues.flatMap { it }
        val completionTexts = capturedElements.map { it.lookupString }

        assertThat(completionTexts)
            .containsExactlyInAnyOrder(
                "apiVersion", "kind", "metadata", "spec"
            )
    }

    @Test
    fun `#addCompletions adds key with types array`() {
        // given - schema with array type like ["string", "null"]
        val schema = """
        {
            "type": "object",
            "properties": {
                "stringOrNull": {
                    "type": ["string", "null"],
                    "description": "Property that can be string or null"
                },
                "regularString": {
                    "type": "string",
                    "description": "Regular string property"
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should provide both properties without throwing exception
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.firstValue
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "stringOrNull", "regularString"
        )
    }

    @Test
    fun `#addCompletions adds keys in items-properties when properties dont exist`() {
        // given - schema with no direct "properties" but has "items.properties"
        val schema = """
        {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Container name"
                    },
                    "image": {
                        "type": "string", 
                        "description": "Container image"
                    },
                    "ports": {
                        "type": "array",
                        "description": "Container ports"
                    }
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should fallback to items.properties and provide container properties
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.firstValue
        val completionTexts = capturedElements.map { it.lookupString }
        
        assertThat(completionTexts).containsExactlyInAnyOrder(
            "name", "image", "ports"
        )
    }

    @Test
    fun `#addCompletions adds empty list when neither properties nor items properties exist`() {
        // given - schema with no "properties" and no "items.properties"
        val schema = """
        {
            "type": "string",
            "description": "Just a string field"
        }
        """.trimIndent()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then - should still call addAllElements once but with empty collection
        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())
        
        val capturedElements = lookupElementCaptor.firstValue
        assertThat(capturedElements).isEmpty()
    }

    @Test
    fun `#addCompletions creates LookupElement with tailText matching schema description`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "containers": {
                    "type": "array",
                    "description": "List of containers belonging to the pod"
                },
                "volumes": {
                    "type": "array",
                    "description": "List of volumes that can be mounted by containers"
                },
                "propertyWithoutDescription": {
                    "type": "string"
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())

        val capturedElements = lookupElementCaptor.firstValue
        
        // containers element
        val containersElement = capturedElements.find { it.lookupString == "containers" }
        assertThat(containersElement).isNotNull
        
        val containersPresentation = LookupElementPresentation()
        containersElement!!.renderElement(containersPresentation)
        assertThat(containersPresentation.tailText)
            .isEqualTo(" (List of containers belonging to the pod)")

        // volumes element
        val volumesElement = capturedElements.find { it.lookupString == "volumes" }
        assertThat(volumesElement)
            .isNotNull
        
        val volumesPresentation = LookupElementPresentation()
        volumesElement!!.renderElement(volumesPresentation)
        assertThat(volumesPresentation.tailText)
            .isEqualTo(" (List of volumes that can be mounted by containers)")

        // property without description
        val propertyWithoutDescElement = capturedElements.find {
            it.lookupString == "propertyWithoutDescription"
        }
        assertThat(propertyWithoutDescElement)
            .isNotNull
        
        val propertyWithoutDescPresentation = LookupElementPresentation()
        propertyWithoutDescElement!!.renderElement(propertyWithoutDescPresentation)
        assertThat(propertyWithoutDescPresentation.tailText)
            .isNull()
    }

    @Test
    fun `#addCompletions creates LookupElement with insertHandler`() {
        // given
        val schema = """
        {
            "type": "object",
            "properties": {
                "testProperty": {
                    "type": "string",
                    "description": "Test property"
                }
            }
        }
        """.trimIndent()

        val lookupElementCaptor = argumentCaptor<Collection<LookupElement>>()

        // when
        KubernetesSchemaCompletions.addCompletions(
            resourceElement,
            emptyList(),
            completionResultSet,
            schemaProvider = { _, _ -> schema }
        )

        // then
        verify(completionResultSet, times(1))
            .addAllElements(lookupElementCaptor.capture())
        val lookupElement = lookupElementCaptor.firstValue.first() as LookupElementBuilder
        assertThat(lookupElement.insertHandler)
            .isNotNull
    }
} 