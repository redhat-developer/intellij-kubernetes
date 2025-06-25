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
import com.redhat.devtools.intellij.kubernetes.editor.util.unquote
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLSequence
import org.json.JSONObject
import org.json.JSONTokener

class ResourcesValidation : LocalInspectionTool() {

    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                when (file) {
                    is YAMLFile -> YAMLValidation.validateResources(file, holder)
                    is JsonFile -> JSONValidation.validateResources(file, holder)
                }
            }
        }
    }

    internal object YAMLValidation: AbstractPsiElementValidation<YAMLDocument>() {

        fun validateResources(file: YAMLFile, holder: ProblemsHolder) {
            file.documents.forEach { yamlDocument ->
                validateResource(yamlDocument, holder)
            }
        }

        private fun validateResource(yamlDocument: YAMLDocument, holder: ProblemsHolder): Boolean {
            val topElement = yamlDocument.topLevelValue as? YAMLMapping ?: return true

            val kind = topElement.getKind()?.text ?: return true
            val apiVersion = topElement.getApiVersion()?.text ?: return true
            val jsonContent = YamlConverter.toJson(yamlDocument)
            if (jsonContent != null) {
                validate(kind, apiVersion, jsonContent, yamlDocument, holder)
            }
            return false
        }

        override fun findElement(jsonPath: String?, root: YAMLDocument): PsiElement? {
            val pathSegments = jsonPath?.split('.') ?: return root
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
    }

    internal object JSONValidation: AbstractPsiElementValidation<JsonObject>() {

        fun validateResources(file: JsonFile, holder: ProblemsHolder) {
            val rootElement = file.topLevelValue

            when (rootElement) {
                is JsonObject ->
                    validateResource(rootElement, holder)

                is JsonArray ->
                    validateResources(rootElement, holder)

                else -> Unit
            }
        }

        private fun validateResources(resources: JsonArray, holder: ProblemsHolder) {
            resources.children.forEach { element ->
                if (element !is JsonObject) {
                    holder.registerProblem(
                        element,
                        "Array contains non-object element. Expected Kubernetes resource object."
                    )
                    return
                }
                validateResource(element, holder)
            }
        }

        private fun validateResource(resource: JsonObject, holder: ProblemsHolder) {
            val kind = resource.getKind()?.text ?: return
            val apiVersion = resource.getApiVersion()?.text ?: return
            try {
                val jsonContent = JSONObject(resource.text)
                validate(kind, apiVersion, jsonContent, resource, holder)
            } catch (e: Exception) {
                holder.registerProblem(resource, "Could not parse JSON object for validation: ${e.message}")
            }
        }

        /**
         * Returns the [PsiElement] that the given (dot delimited) jsonPath is pointing to within the given root element.
         *
         * @param jsonPath The dot-separated path to the desired element (e.g., "metadata.name", "spec.containers.0.image").
         * @param root The root [PsiElement] of the JSON structure (usually a [JsonObject] or [JsonArray]).
         * @return The [PsiElement] at the specified path, or `null` if the path is invalid or the element is not found.
         */
        override fun findElement(jsonPath: String?, root: JsonObject): PsiElement? {
            val pathSegments = jsonPath?.split('.') ?: return root
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

    abstract class AbstractPsiElementValidation<RESOURCE: PsiElement>() {

        /**
         * Validates the given Kubernetes resource content against the appropriate schema.
         * Registers validation errors with the given holder.
         *
         * @param kind The kind of the Kubernetes resource.
         * @param apiVersion The apiVersion of the Kubernetes resource.
         * @param jsonContent The content of the Kubernetes resource as an org.json.JSONObject.
         * @param resource The psi Element to highlight in the editor.
         * @param holder The ProblemsHolder to register problems.
         */
        open fun validate(
            kind: String,
            apiVersion: String,
            jsonContent: JSONObject,
            resource: RESOURCE,
            holder: ProblemsHolder
        ) {
            val unquotedKind = unquote(kind) ?: return
            val unquotedApiVersion = unquote(apiVersion) ?: return
            val schemaString = KubernetesSchema.get(unquotedKind, unquotedApiVersion)
            if (schemaString == null) {
                val kindElement = resource.getKind() ?: resource
                holder.registerProblem(
                    kindElement,
                    "No Kubernetes schema found for kind: '$unquotedKind' (apiVersion: '${unquotedApiVersion}')."
                )
                return
            }

            try {
                val schema = SchemaLoader.load(JSONObject(JSONTokener(schemaString)))
                schema.validate(jsonContent)
            } catch (e: ValidationException) {
                e.allMessages.forEach { message ->
                    registerProblem(message, resource, holder)
                }
            }
        }

        /* protected for testing purposes */
        abstract fun findElement(jsonPath: String?, root: RESOURCE): PsiElement?

        /* protected for testing purposes */
        protected open fun registerProblem(message: String, resource: RESOURCE, holder: ProblemsHolder) {
            if (isCascadingError(message)) {
                return
            }

            val problemPath = extractJsonPath(message) ?: return
            val toHighlight = findElement(problemPath, resource) ?: return
            holder.registerProblem(toHighlight, message)
        }

        /* protected for testing purposes */
        protected open fun isCascadingError(message: String): Boolean {
            return message.contains("expected: null")
        }

        /* protected for testing purposes */
        protected open fun extractJsonPath(message: String): String? {
            val regex = Regex("#/([^:]+):") // Matches something like #/path/to/field:
            val match = regex.find(message)
            return match
                ?.groupValues?.get(1)
                ?.replace("/", ".")
        }
    }
}