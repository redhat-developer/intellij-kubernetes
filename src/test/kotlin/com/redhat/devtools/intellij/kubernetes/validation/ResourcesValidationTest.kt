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
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.psi.PsiElement
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createDocument
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createDocuments
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonArray
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonObject
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonObjectForPairs
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createJsonProperty
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMetadata
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMappingForPairs
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequence
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequenceItem
import com.redhat.devtools.intellij.kubernetes.editor.util.unquote
import com.redhat.devtools.intellij.kubernetes.validation.ResourcesValidation.AbstractPsiElementValidation
import com.redhat.devtools.intellij.kubernetes.validation.ResourcesValidation.JSONValidation
import com.redhat.devtools.intellij.kubernetes.validation.ResourcesValidation.YAMLValidation
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLValue
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class ResourcesValidationTest {

    @Mock
    private lateinit var problemsHolder: ProblemsHolder

    @Mock
    private lateinit var yamlDocument: YAMLDocument
    private lateinit var yamlDocumentTopLevelValue: YAMLMapping

    @Mock
    private lateinit var yamlFile: YAMLFile

    @Mock
    private lateinit var jsonFile: JsonFile

    @Mock
    private lateinit var jsonObject: JsonObject

    @Mock
    private lateinit var jsonArray: JsonArray

    private lateinit var validation: ResourcesValidation
    private lateinit var elementValidation: TestablePsiElementValidation


    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        this.yamlDocumentTopLevelValue = mock<YAMLMapping>()
        doReturn(yamlDocumentTopLevelValue)
            .whenever(yamlDocument).topLevelValue
        this.validation = ResourcesValidation()
        this.elementValidation = TestablePsiElementValidation(mock())
    }

    @Test
    fun `#isEnabledByDefault should return true`() {
        // when
        val result = validation.isEnabledByDefault()

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `#buildVisitor should return visitor that is not null`() {
        // when
        val visitor = validation.buildVisitor(problemsHolder, false)

        // then
        assertThat(visitor).isNotNull()
    }

    @Test
    fun `#extractJsonPath should extract path from validation message`() {
        // given
        val message = "#/spec/containers/0/image: expected type: String, found: Integer"
        // when

        val path = elementValidation.extractJsonPath(message)

        // then
        assertThat(path).isEqualTo("spec.containers.0.image")
    }

    @Test
    fun `#extractJsonPath should extract complex path from validation message`() {
        // given
        val message = "#/metadata/labels/app.kubernetes.io~1name: invalid value"

        // when
        val path = elementValidation.extractJsonPath(message)

        // then
        assertThat(path).isEqualTo("metadata.labels.app.kubernetes.io~1name")
    }

    @Test
    fun `#extractJsonPath should return null for message without path`() {
        // given
        val message = "Some validation error without path"

        // when
        val path = elementValidation.extractJsonPath(message)

        // then
        assertThat(path).isNull()
    }

    @Test
    fun `#extractJsonPath should return null for malformed path`() {
        // given
        val message = "Some error without proper #/ prefix"

        // when
        val path = elementValidation.extractJsonPath(message)

        // then
        assertThat(path).isNull()
    }

    @Test
    fun `#isCascadingError should return true for cascading error messages`() {
        // given
        val message = "cascading error with expected: null"

        // when
        val result = elementValidation.isCascadingError(message)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `#isCascadingError should return false for non-cascading error messages`() {
        // given
        val message = "Luke Skywalker embraced the dark side of the force"

        // when
        val result = elementValidation.isCascadingError(message)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `#isCascadingError should return false for empty message`() {
        // given
        val message = ""

        // when
        val result = elementValidation.isCascadingError(message)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `#findElement should return null for empty path with YAMLDocument`() {
        // given
        val jsonPath = ""

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#findElement should handle simple path with YAMLDocument`() {
        // given
        val jsonPath = "metadata"
        val metadata = yamlDocumentTopLevelValue.createMetadata()

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isEqualTo(metadata)
    }

    @Test
    fun `#findElement should handle nested path with YAMLDocument`() {
        // given
        val jsonPath = "metadata.name"
        val metadata = yamlDocumentTopLevelValue.createMetadata()
        val nameValue = mock<YAMLValue>()
        createYAMLKeyValue("name", nameValue, metadata)

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isEqualTo(nameValue)
    }

    @Test
    fun `#findElement should return array item given path with index in path with YAMLDocument`() {
        // given
        val jsonPath = "spec.containers.1.image"
        val yodaContainer = createYAMLMapping(listOf(
            createYAMLKeyValue("image", "yoda")
        ))
        val lukeContainer = createYAMLMapping(listOf(
            createYAMLKeyValue("image", "luke")
        ))
        createYAMLKeyValue("spec",
            createYAMLMapping(listOf(
                createYAMLKeyValue("containers",
                    createYAMLSequence(
                        listOf(
                            createYAMLSequenceItem(yodaContainer),
                            createYAMLSequenceItem(lukeContainer)
                        )
                    )
                )
            )),
            yamlDocumentTopLevelValue)

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result?.text).isEqualTo("luke")
    }

    @Test
    fun `#findElement returns null for given path with invalid array index with YAMLDocument`() {
        // given
        val jsonPath = "spec.containers.invalid"

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#findElement returns JsonObject for given simple path`() {
        // given
        val jsonPath = "metadata"
        val metadata = jsonObject.createMetadata()

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result).isEqualTo(metadata)
    }

    @Test
    fun `#findElement returns array item given path with index in path with JsonObject`() {
        // given
        val jsonPath = "spec.containers.1.image"
        val yodaContainer = createJsonObject(listOf(
            createJsonProperty("image", "yoda")
        ))
        val lukeContainer = createJsonObject(listOf(
            createJsonProperty("image", "luke")
        ))

        createJsonProperty("spec",
            createJsonObject(listOf(
                createJsonProperty("containers",
                    createJsonArray(
                        listOf(
                            yodaContainer,
                            lukeContainer
                        )
                    )
                )
            )),
            jsonObject
        )

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result?.text).isEqualTo("luke")
    }

    @Test
    fun `#findElement returns jsonValue for given nested path`() {
        // given
        val jsonPath = "metadata.name"
        val value = mock<JsonValue>()
        createJsonProperty("name", value, jsonObject.createMetadata())

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result).isEqualTo(value)
    }

    @Test
    fun `#findElement returns null for given empty path with JsonObject `() {
        // given
        val jsonPath = ""

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `#findElement returns root object when given null path with JsonObject `() {
        // given
        val jsonPath = null

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result).isEqualTo(jsonObject)
    }

    @Test
    fun `#registerProblem does not register cascading errors`() {
        // given
        val message = "Some error with expected: null"
        val root = mock<PsiElement>()

        // when
        elementValidation.registerProblem(message, root, problemsHolder)

        // then
        verify(problemsHolder, never())
            .registerProblem(any(), any<String>())
    }

    @Test
    fun `#registerProblem does not register non-cascading error if message has no path`() {
        // given
        val message = "This message has no path"
        val root = mock<PsiElement>()

        // when
        elementValidation.registerProblem(message, root, problemsHolder)

        // then
        verify(problemsHolder, never())
            .registerProblem(any(), any<String>())
    }

    @Test
    fun `#registerProblem does not register non-cascading error if there's no element to highlight`() {
        // given
        val message = "Luke Skywalker is a member of the #/guild/of/the: jedis"
        val elementValidation = TestablePsiElementValidation(null) // no element found

        // when
        elementValidation.registerProblem(message,  mock<PsiElement>(), problemsHolder)

        // then
        verify(problemsHolder, never())
            .registerProblem(any(), any<String>())
    }

    @Test
    fun `#registerProblem registers non-cascading errors`() {
        // given
        val message = "Luke Skywalker is a member of the #/guild/of/the: jedis"

        // when
        elementValidation.registerProblem(message, mock<PsiElement>(), problemsHolder)

        // then
        verify(problemsHolder)
            .registerProblem(any(), eq(message))
    }

    // NEW TESTS FOR ADDITIONAL COVERAGE

    @Test
    fun `YAMLValidation#validateResources is processing all documents in YAML file`() {
        // given
        yamlFile.createDocuments(listOf(
            createDocument(null),
            createDocument(null)
        ))

        // when
        YAMLValidation.validateResources(yamlFile, problemsHolder)

        // then
        verify(yamlFile).documents
    }

    @Test
    fun `YAMLValidation#validateResources skips documents without top level mapping`() {
        // given
        val document = createDocument(null)
        yamlFile.createDocuments(listOf(document))

        // when
        YAMLValidation.validateResources(yamlFile, problemsHolder)

        // then
        verify(document).topLevelValue
        verifyNoMoreInteractions(problemsHolder)
    }

    @Test
    fun `YAMLValidation#validateResources skips documents without kind`() {
        // given
        val mapping = mock<YAMLMapping>() {
            on { getKeyValueByKey("kind") } doReturn null
        }
        yamlFile.createDocuments(listOf(
            createDocument(mapping))
        )

        // when
        YAMLValidation.validateResources(yamlFile, problemsHolder)

        // then
        verify(mapping).getKeyValueByKey("kind")
        verifyNoMoreInteractions(problemsHolder)
    }

    @Test
    fun `YAMLValidation#validateResources skips documents without apiVersion`() {
        // given
        val mapping = createYAMLMappingForPairs(listOf(
            "kind" to createYAMLKeyValue("Pod"),
            "apiVersion" to null
        ))
        val document = createDocument(mapping)
        yamlFile.createDocuments(listOf(document))
        whenever(document.topLevelValue)
            .thenReturn(mapping)

        // when
        YAMLValidation.validateResources(yamlFile, problemsHolder)

        // then
        verify(mapping)
            .getKeyValueByKey("apiVersion")
        verifyNoMoreInteractions(problemsHolder)
    }

    @Test
    fun `JSONValidation#validateResources should validate JsonObject that is root element`() {
        // given
        val jsonObject = createJsonObjectForPairs(
            "{\"kind\":\"Pod\",\"apiVersion\":\"v1\"}",
            listOf(
                "kind" to createJsonProperty("\"Pod\""),
                "apiVersion" to createJsonProperty("\"v1\"")
        ))
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonObject)

        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(jsonObject)
            .findProperty("kind")
        verify(jsonObject)
            .findProperty("apiVersion")
    }

    @Test
    fun `JSONValidation#validateResources should validate JsonArray that is root element`() {
        // given
        val jsonObject = createJsonObjectForPairs(
            "{\"kind\":\"Pod\",\"apiVersion\":\"v1\"}",
            listOf(
            "kind" to createJsonProperty("\"Pod\""),
            "apiVersion" to createJsonProperty("\"v1\"")
        ))
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonObject)
        val jsonArray = createJsonArray(listOf(
            jsonObject
        ))
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonArray)

        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(jsonArray).children
        verify(jsonObject).findProperty("kind")
        verify(jsonObject).findProperty("apiVersion")
    }

    @Test
    fun `JSONValidation#validateResources registers problem for non-object in array`() {
        // given
        val nonObjectElement = mock<JsonValue>()
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonArray)
        whenever(jsonArray.children)
            .thenReturn(arrayOf(nonObjectElement))

        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(problemsHolder).registerProblem(
            eq(nonObjectElement),
            eq("Array contains non-object element. Expected Kubernetes resource object.")
        )
    }

    @Test
    fun `JSONValidation#validateResources skips objects without kind`() {
        // given
        val jsonObject = createJsonObjectForPairs(
            "{\"apiVersion\":\"v1\"}",
            listOf(
                "kind" to null,
                "apiVersion" to createJsonProperty("\"v1\"")
            )
        )
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonObject)
        val jsonArray = createJsonArray(listOf(
            jsonObject
        ))
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonArray)

        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(jsonObject)
            .findProperty("kind")
        verify(jsonObject, never())
            .text
    }

    @Test
    fun `JSONValidation#validateResources skips object without apiVersion`() {
        // given
        val jsonObject = createJsonObjectForPairs(
            "{\"kind\":\"Pod\"}",
            listOf(
                "kind" to createJsonProperty("\"Pod\"")
            )
        )
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonObject)
        val jsonArray = createJsonArray(listOf(
            jsonObject
        ))
        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonArray)
        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(jsonObject)
            .findProperty("apiVersion")
        verify(jsonObject, never())
            .text
    }

    @Test
    fun `JSONValidation#validateResources should register problem for JSON parsing error`() {
        // given
        val jsonObject = createJsonObjectForPairs(
            "\"invalid json\"",
            listOf(
                "kind" to createJsonProperty("\"Pod\""),
                "apiVersion" to createJsonProperty("\"v1\"")
            )
        )

        whenever(jsonFile.topLevelValue)
            .thenReturn(jsonObject)

        // when
        JSONValidation.validateResources(jsonFile, problemsHolder)

        // then
        verify(problemsHolder).registerProblem(
            eq(jsonObject),
            argThat<String> { message -> message.startsWith("Could not parse JSON object for validation:") }
        )
    }

    @Test
    fun `JSONValidation#validate is registering problem when no schema found`() {
        // given
        val kind = "UnknownKind"
        val apiVersion = "unknown/v1"
        val jsonContent = JSONObject("{\"kind\":\"$kind\",\"apiVersion\":\"$apiVersion\"}")
        val jsonObject = createJsonObjectForPairs(
            "{\"kind\":\"$kind\",\"apiVersion\":\"$apiVersion\"}",
            listOf(
                "kind" to createJsonProperty("\"$kind\""),
                "apiVersion" to createJsonProperty("\"$apiVersion\"")
            )
        )
        // when
        JSONValidation.validate(kind, apiVersion, jsonContent, jsonObject, problemsHolder)

        // then
        verify(problemsHolder).registerProblem(
            argThat<PsiElement> { element ->
                unquote((element as? JsonValue)?.text) == kind
            },
            argThat<String> { problem ->
                problem.contains("No Kubernetes schema found") &&
                        problem.contains(kind) &&
                        problem.contains(apiVersion)
            }
        )
    }

    @Test
    fun `JSONValidation#validate is using resource as fallback when no kind element found`() {
        // given
        val kind = "UnknownKind"
        val apiVersion = "unknown/v1"
        val jsonContent = JSONObject("{\"kind\":\"$kind\",\"apiVersion\":\"$apiVersion\"}")
        val resource = mock<JsonObject>()

        // when
        JSONValidation.validate(kind, apiVersion, jsonContent, resource, problemsHolder)

        // then
        verify(problemsHolder).registerProblem(
            eq(resource), // is using resource as fallback
            argThat<String> { problem ->
                problem.contains("No Kubernetes schema found") &&
                        problem.contains(kind) &&
                        problem.contains(apiVersion)
            }
        )
    }

    @Test
    fun `YAMLValidation#findElement should return root document when given null path`() {
        // given
        val jsonPath = null

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isEqualTo(yamlDocument)
    }

    @Test
    fun `YAMLValidation#findElement should handle array index out of bounds`() {
        // given
        val jsonPath = "spec.containers.10" // index out of bounds
        createYAMLKeyValue("spec",
            createYAMLMapping(listOf(
                createYAMLKeyValue("containers",
                    createYAMLSequence(
                        listOf(
                            createYAMLSequenceItem(createYAMLMapping(emptyList()))
                        )
                    )
                )
            )),
            yamlDocumentTopLevelValue)

        // when
        val result = YAMLValidation.findElement(jsonPath, yamlDocument)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `JSONValidation#findElement should handle array index out of bounds`() {
        // given
        val jsonPath = "spec.containers.10" // index out of bounds
        createJsonProperty("spec",
            createJsonObject(listOf(
                createJsonProperty("containers",
                    createJsonArray(
                        listOf(
                            createJsonObject(emptyList())
                        )
                    )
                )
            )),
            jsonObject
        )

        // when
        val result = JSONValidation.findElement(jsonPath, jsonObject)

        // then
        assertThat(result).isNull()
    }

    class TestablePsiElementValidation(private val elementFound: PsiElement?): AbstractPsiElementValidation<PsiElement>() {
        public override fun extractJsonPath(message: String): String? {
            return super.extractJsonPath(message)
        }

        public override fun isCascadingError(message: String): Boolean {
            return super.isCascadingError(message)
        }

        public override fun registerProblem(message: String, resource: PsiElement, holder: ProblemsHolder) {
            return super.registerProblem(message, resource, holder)
        }

        override fun validate(
            kind: String,
            apiVersion: String,
            jsonContent: JSONObject,
            resource: PsiElement,
            holder: ProblemsHolder
        ) {
            return super.validate(kind, apiVersion, jsonContent, resource, holder)
        }

        override fun findElement(jsonPath: String?, root: PsiElement): PsiElement? {
            return elementFound
        }
    }


    private fun createYAMLKeyValue(textValue: String): YAMLKeyValue {
        val valueElement = mock<YAMLValue> {
            on { mock.text } doReturn textValue
        }
        return mock<YAMLKeyValue> {
            on { mock.value } doReturn valueElement
        }
    }

    private fun createJsonProperty(textValue: String): JsonProperty {
        val valueElement = mock<JsonValue> {
            on { mock.text } doReturn textValue
        }
        return mock<JsonProperty> {
            on { mock.value } doReturn valueElement
        }
    }

}