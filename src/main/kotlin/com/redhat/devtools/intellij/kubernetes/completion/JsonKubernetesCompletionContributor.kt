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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.redhat.devtools.intellij.kubernetes.completion.KubernetesSchemaCompletions.getCurrentElement
import com.redhat.devtools.intellij.kubernetes.editor.util.getResource

class JsonKubernetesCompletionContributor : CompletionContributor() {

    init {
        // Register completion for JSON property names and values
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(
                PlatformPatterns.psiFile(JsonFile::class.java)),
            JsonSchemaCompletionProvider()
        )
    }

    private class JsonSchemaCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            try {
                ReadAction.compute<Unit, Exception> {
                    val currentElement = getCurrentElement(parameters)

                    val resource = currentElement.getResource() as? JsonObject
                        ?: return@compute
                    val currentPath = pathFromRoot(currentElement, resource)

                    logger<JsonKubernetesCompletionContributor>().debug("Providing JSON completions for path: '$currentPath'")
                    KubernetesSchemaCompletions.addCompletions(resource, currentPath, result)
                }
            } catch (e: Exception) {
                logger<JsonKubernetesCompletionContributor>().debug("Error in JSON completion", e)
            }
        }

        /**
         * Builds the JSON path from the resource root to the target element.
         */
        private fun pathFromRoot(target: PsiElement, root: JsonObject): List<String> {
            val pathSegments = mutableListOf<String>()
            var current = target

            // Walk up the PSI tree to build the path
            while (current != root
                && current.parent != null) {
                when (current.parent) {
                    is JsonProperty -> {
                        val property = current.parent as JsonProperty
                        // Add the property name to the path if we're not at the name element
                        if (current != property.nameElement) {
                            pathSegments.add(0, property.name)
                        }
                    }
                    is JsonArray -> {
                        // Handle array indices - find the index of the current element
                        val array = current.parent as JsonArray
                        val index = array.valueList.indexOf(current)
                        if (index >= 0) {
                            pathSegments.add(0, index.toString())
                        }
                    }
                }
                current = current.parent
            }

            return pathSegments
        }
    }
} 