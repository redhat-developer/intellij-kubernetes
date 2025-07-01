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

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.kubernetes.editor.util.getApiVersion
import com.redhat.devtools.intellij.kubernetes.editor.util.getKind
import com.redhat.devtools.intellij.kubernetes.editor.util.unquote
import com.redhat.devtools.intellij.kubernetes.validation.KubernetesSchema
import org.json.JSONArray
import org.json.JSONObject

object KubernetesSchemaCompletions {

    private val logger = logger<KubernetesSchemaCompletions>()

    /**
     * Provides completion suggestions based on the Kubernetes schema for the given context.
     * 
     * @param resource The root resource element (YAMLMapping or JsonObject)
     * @param path The JSON path to the current completion position (e.g., "spec.containers")
     * @param results The completion result set to add suggestions to
     * @param schemaProvider Optional schema provider for testing (defaults to KubernetesSchema.get)
     */
    fun addCompletions(
        resource: PsiElement,
        path: List<String>,
        results: CompletionResultSet,
        schemaProvider: (String, String) -> String? = KubernetesSchema::get
    ) {
        try {
            val kind = resource.getKind()
                ?.text?.let { unquote(it) }
                ?: return
            val apiVersion = resource.getApiVersion()
                ?.text?.let { unquote(it) }
                ?: return
            val schemaString = schemaProvider(kind, apiVersion)
            if (schemaString == null) {
                logger.debug("No schema found for kind: '$kind', apiVersion: '$apiVersion'")
                return
            }

            val schema = JSONObject(schemaString)

            val keyCompletions = getKeyCompletions(path, schema)
            results.addAllElements(keyCompletions)

            // Add enum values if we're completing a value (not a key)
            if (path.isNotEmpty()) {
                val valueCompletions = getValueCompletions(path, schema)
                results.addAllElements(valueCompletions)
            }
        } catch (e: Exception) {
            logger.debug("Could not provide schema completions for $path", e)
        }
    }

    private fun getValueCompletions(path: List<String>, schema: JSONObject): List<LookupElement> {
        val valueCompletions = getEnumValues(path, schema)
            .map { enumValue ->
                LookupElementBuilder.create(enumValue)
                    .withIcon(AllIcons.Nodes.Enum)
                    .withTypeText("enum value", true)
            }
        return valueCompletions
    }

    private fun getKeyCompletions(path: List<String>, schema: JSONObject): List<LookupElement> {
        return getCompletions(path, schema).map { suggestion ->
            var lookupElement = LookupElementBuilder.create(suggestion.name)
                .withTypeText(suggestion.type, true)
                .withIcon(AllIcons.Nodes.Field)
                .withInsertHandler { context, _ ->
                    append(": ", 2, context)
                }

            if (suggestion.description != null) {
                lookupElement = lookupElement.withTailText(" (${suggestion.description})", true)
            }

            lookupElement
        }
    }

    private fun append(toAppend: String, shiftCursorColumn: Int, context: InsertionContext) {
        val document = context.document
        val selectionEndOffset = context.selectionEndOffset

        val lineNumber = document.getLineNumber(context.startOffset)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineStart = document.getLineStartOffset(lineNumber)
        val line = document.getText(TextRange(lineStart, lineEnd))
        if (line.indexOf(toAppend, selectionEndOffset - lineStart) >= 0) {
            // dont insert if already exists
            return
        }
        document.insertString(selectionEndOffset, toAppend)
        context.editor.caretModel.moveCaretRelatively(shiftCursorColumn, 0, false, false, true)
    }

    /**
     * Returns completion suggestions for a given path in the schema.
     * If the full path is invalid (e.g., contains incomplete property names),
     * it tries progressively shorter paths until it finds a valid schema location.
     *
     * @param path The JSON path to the current completion position.
     * @param schema The Kubernetes schema as a JSONObject.
     * @return A list of [CompletionSuggestion] objects.
     */
    private fun getCompletions(path: List<String>, schema: JSONObject): List<CompletionSuggestion> {
        val schemaAtPath = findSchema(path, schema)
        val schemaToUse = if (schemaAtPath != null) {
            schemaAtPath
        } else {
            logger.debug("No valid schema path found for '$path', using root-level properties")
            schema
        }
        return createSuggestions(schemaToUse)
    }

    /**
     * Returns enum values for a given path in the schema. If none was found, an empty list is returned.
     *
     * @param path the path at which the schema enum values should be returns
     * @param schema the schema to get the enum values from
     * @return the enum values for the given path, or an empty list if none were found
     */
    private fun getEnumValues(path: List<String>, schema: JSONObject): List<String> {
        val schemaAtPath = findSchema(path, schema)
        return if (schemaAtPath != null) {
            val enumArray = schemaAtPath.optJSONArray("enum") ?: return emptyList()
            (0 until enumArray.length()).map { enumArray.getString(it) }
        } else {
            emptyList()
        }
    }

    /**
     * Finds the schema object at the given path using progressive fallback.
     * If the full path is invalid, tries progressively shorter paths until finding a valid schema location.
     * Returns null if no valid path is found.
     */
    private fun findSchema(path: List<String>, schema: JSONObject): JSONObject? {
        if (path.isEmpty()) {
            return schema
        }

        // Try progressively shorter paths until we find a valid schema location
        for (i in path.size downTo 0) {
            val currentPathSegments = path.take(i)
            var currentSchema = schema
            var isValidPath = true

            // Navigate to the schema location for the current path
            for (segment in currentPathSegments) {
                val nextSchema = getSchemaForProperty(segment, currentSchema)
                if (nextSchema == null) {
                    isValidPath = false
                    break
                }
                currentSchema = nextSchema
            }

            if (isValidPath) {
                if (currentPathSegments.isNotEmpty()) {
                    logger.debug("Using schema path: '${currentPathSegments.joinToString(".")}' for completion")
                }
                return currentSchema
            }
        }

        return null
    }

    /**
     * Navigates through the schema to find the schema for a specific property.
     */
    private fun getSchemaForProperty(property: String, schema: JSONObject): JSONObject? {
        val properties = schema.optJSONObject("properties")
        if (properties != null
            && properties.has(property)) {
            return properties.getJSONObject(property)
        }

        // Handle array items
        if (schema.has("items")) {
            val items = schema.get("items")
            if (items is JSONObject) {
                return getSchemaForProperty(property, items)
            }
        }

        if (schema.has("additionalProperties")) {
            val additionalProps = schema.get("additionalProperties")
            if (additionalProps is JSONObject) {
                return getSchemaForProperty(property, additionalProps)
            }
        }

        if (schema.has("\$ref")) {
            val ref = schema.getString("\$ref")
            // For now, we don't resolve external references
            logger.debug("Encountered unresolved reference: $ref")
        }

        return null
    }

    /**
     * Extracts property suggestions from a schema object.
     */
    private fun createSuggestions(schema: JSONObject): List<CompletionSuggestion> {
        val properties = schema.optJSONObject("properties")
            ?: schema.optJSONObject("items")?.optJSONObject("properties")
            ?: return emptyList<CompletionSuggestion>()
        return properties.keySet().map { key ->
            val propertySchema = properties.getJSONObject(key)
            val type = getSchemaType(propertySchema)
            val description = propertySchema.optString("description", null)
            CompletionSuggestion(key, type, description)
        }
        .sortedBy { it.name }
    }

    /**
     * Determines the type of a schema property.
     */
    private fun getSchemaType(propertySchema: JSONObject): String {
        return when {
            propertySchema.has("type") ->
                getSchemaType(propertySchema.get("type"))
            propertySchema.has("properties") ->
                "object"
            propertySchema.has("items") ->
                "array"
            propertySchema.has("enum") ->
                "enum"
            propertySchema.has("\$ref") ->
                "reference"
            else ->
                "unknown"
        }
    }

    private fun getSchemaType(typeValue: Any): String {
        return when (typeValue) {
            is String ->
                typeValue
            is org.json.JSONArray -> {
                // Handle multiple types like ["string", "null"]
                // Return the first non-null type
                val type = getFirstNonNullType(typeValue)
                if (type != null) {
                    return type
                }
                // If all types are "null", return the first one
                if (typeValue.length() > 0) {
                    typeValue.getString(0)
                } else {
                    "unknown"
                }
            }
            else ->
                "unknown"
        }
    }

    private fun getFirstNonNullType(typeValues: JSONArray): String? {
        for (i in 0 until typeValues.length()) {
            val type = typeValues.getString(i)
            if (type != "null") {
                return type
            }
        }
        return null
    }

    data class CompletionSuggestion(
        val name: String,
        val type: String,
        val description: String?
    )

    fun getCurrentElement(parameters: CompletionParameters): PsiElement {
        val position = parameters.position
        return parameters.originalPosition ?: position
    }
}