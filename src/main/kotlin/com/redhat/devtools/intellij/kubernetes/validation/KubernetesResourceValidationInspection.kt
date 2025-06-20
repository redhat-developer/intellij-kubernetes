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

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.redhat.devtools.intellij.kubernetes.editor.util.getApiVersion
import com.redhat.devtools.intellij.kubernetes.editor.util.getKind
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLSequence
import org.json.JSONObject
import org.json.JSONTokener

class KubernetesResourceValidationInspection : LocalInspectionTool() {

    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                when (file) {
                    is YAMLFile -> validate(file, holder)
                    is JsonFile -> validate(file, holder)
                }
            }
        }
    }

    private fun validate(file: YAMLFile, holder: ProblemsHolder) {
        file.documents.forEach { yamlDocument ->
            val topElement = yamlDocument.topLevelValue as? YAMLMapping ?: return

            val kind = topElement.getKind()?.text ?: return
            val apiVersion = topElement.getApiVersion()?.text ?: return
            val jsonContent = YamlConverter.toJson(yamlDocument)
            if (jsonContent != null) {
                validate(kind, apiVersion, jsonContent, yamlDocument, holder)
            }
        }
    }

    private fun validate(file: JsonFile, holder: ProblemsHolder) {
        val rootElement = file.topLevelValue

        when (rootElement) {
            is JsonObject ->
                validateJsonObject(rootElement, holder)

            is JsonArray -> // Case: JSON file containing an array of K8s resources
                validateJsonArray(rootElement, holder)

            else -> Unit
        }
    }

    private fun validateJsonArray(rootElement: JsonArray, holder: ProblemsHolder) {
        rootElement.children.forEach { element ->
            if (element !is JsonObject) {
                holder.registerProblem(
                    element,
                    "Array contains non-object element; expected Kubernetes resource object."
                )
                return
            }

            val kind = element.getKind()?.text
            val apiVersion = element.getApiVersion()?.text
            if (apiVersion != null
                && kind != null) {
                try {
                    val jsonContent = JSONObject(element.text)
                    validate(kind, apiVersion, jsonContent, element, holder)
                } catch (e: Exception) {
                    holder.registerProblem(
                        element,
                        "Could not parse JSON object in array for validation: ${e.message}"
                    )
                }
            }
        }
    }

    private fun validateJsonObject(toHighlight: JsonObject, holder: ProblemsHolder) {
        val apiVersion = toHighlight.getApiVersion()?.text ?: return
        val kind = toHighlight.getKind()?.text ?: return
        try {
            val jsonContent = JSONObject(toHighlight.text)
            validate(apiVersion, kind, jsonContent, toHighlight, holder)
        } catch (e: Exception) {
            holder.registerProblem(toHighlight, "Could not parse JSON object for validation: ${e.message}")
        }
    }

    /**
     * Validates the given Kubernetes resource content against the appropriate schema.
     * Registers validation errors with the given holder.
     *
     * @param kind The kind of the Kubernetes resource.
     * @param apiVersion The apiVersion of the Kubernetes resource.
     * @param jsonContent The content of the Kubernetes resource as an org.json.JSONObject.
     * @param toHighlight The psi Element to highlight in the editor.
     * @param holder The ProblemsHolder to register problems.
     */
    private fun validate(
        kind: String,
        apiVersion: String,
        jsonContent: JSONObject,
        toHighlight: PsiElement,
        holder: ProblemsHolder
    ) {
        val schemaString = KubernetesSchema.get(kind, apiVersion)
        if (schemaString == null) {
            val kindElement = toHighlight.getKind() ?: toHighlight
            holder.registerProblem(
                kindElement,
                "No Kubernetes schema found for kind: '$kind' (apiVersion: '$apiVersion')."
            )
            return
        }

        try {
            val schema = SchemaLoader.load(JSONObject(JSONTokener(schemaString)))
            schema.validate(jsonContent)
        } catch (e: ValidationException) {
            e.allMessages.forEach { message ->
                registerProblem(message, toHighlight, holder)
            }
        }
    }

    private fun registerProblem(message: String, root: PsiElement, holder: ProblemsHolder) {
        if (isCascadingError(message)) {
            return
        }
        val problemPath = extractJsonPath(message)
        val toHighlight = when (root) {
            is YAMLDocument -> {
                if (problemPath != null) {
                    findElement(problemPath, root)
                } else {
                    null
                }
            }

            is JsonObject -> {
                if (problemPath != null) {
                    findElement(problemPath, root)
                } else {
                    null
                }
            }

            is JsonArray -> null // Highlighting for array items needs more context (handled by validating `element` directly)
            else -> null
        } ?: root // Fallback to the top-level element if path mapping fails

        holder.registerProblem(toHighlight, message)
    }

    private fun isCascadingError(message: String): Boolean {
        return message.contains("expected: null")
    }

    private fun extractJsonPath(message: String): String? {
        val regex = Regex("#/([^:]+):") // Matches something like #/path/to/field:
        val match = regex.find(message)
        return match
            ?.groupValues?.get(1)
            ?.replace("/", ".")
    }

    fun findElement(jsonPath: String, root: YAMLDocument): PsiElement? {
        val pathSegments = jsonPath.split('.')
        var currentElement: YAMLPsiElement? = root.topLevelValue ?: return null

        for (segment in pathSegments) {
            currentElement = when (currentElement) {
                null -> return null

                is YAMLMapping -> {
                    val keyValue = currentElement.getKeyValueByKey(segment)
                    keyValue?.value // Move to the value of the key
                }
                is YAMLSequence -> {
                    val index = segment.toIntOrNull()
                    if (index != null && index >= 0 && index < currentElement.items.size) {
                        currentElement.items[index].value // Move to the item at index
                    } else {
                        null // Invalid path segment for sequence
                    }
                }
                else ->
                    null // Path cannot be traversed further
            }
        }

        return currentElement
    }

    /**
     * Returns the [PsiElement] that the given (dot delimited) jsonPath is pointing to within the given root element.
     *
     * @param jsonPath The dot-separated path to the desired element (e.g., "metadata.name", "spec.containers.0.image").
     * @param root The root [PsiElement] of the JSON structure (usually a [JsonObject] or [JsonArray]).
     * @return The [PsiElement] at the specified path, or `null` if the path is invalid or the element is not found.
     */
    private fun findElement(jsonPath: String, root: JsonObject): PsiElement? {
        val pathSegments = jsonPath.split('.')
        var currentElement: PsiElement? = root

        for (segment in pathSegments) {
            when (currentElement) {
                is JsonObject ->
                    currentElement = currentElement.findProperty(segment)?.value

                is JsonArray -> {
                    val index = segment.toIntOrNull()
                    if (index != null
                        && index >= 0
                        && index < currentElement.children.size) {
                        currentElement = currentElement.children[index]
                    } else {
                        return null // Invalid path segment for array
                    }
                }
                else ->
                    return null // Path cannot be traversed further
            }
            if (currentElement == null) {
                return null
            }
        }
        return currentElement
    }
}