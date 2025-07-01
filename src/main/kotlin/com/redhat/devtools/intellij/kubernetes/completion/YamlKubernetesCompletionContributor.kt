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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.redhat.devtools.intellij.kubernetes.completion.KubernetesSchemaCompletions.getCurrentElement
import com.redhat.devtools.intellij.kubernetes.editor.util.getResource
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class YamlKubernetesCompletionContributor : CompletionContributor() {

    init {
        // Register completion for YAML key completion (property names)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(
                PlatformPatterns.psiFile(YAMLFile::class.java)),
            YamlSchemaCompletionProvider()
        )
    }

    private class YamlSchemaCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            try {
                ReadAction.compute<Unit, Exception> {
                    val currentElement = getCurrentElement(parameters)

                    val resource = currentElement.getResource() as? YAMLMapping
                        ?: return@compute
                    val currentPath = pathFromRoot(currentElement, resource)

                    logger<YamlKubernetesCompletionContributor>().debug("Providing completions for path: '$currentPath'")
                    KubernetesSchemaCompletions.addCompletions(resource, currentPath, result)
                }
            } catch (e: Exception) {
                logger<YamlKubernetesCompletionContributor>().debug("Error in YAML completion", e)
            }
        }

        /**
         * Builds the JSON path from the resource root to the current position.
         */
        private fun pathFromRoot(target: PsiElement, root: YAMLMapping): List<String> {
            val pathSegments = mutableListOf<String>()
            var current = target

            // Walk up the PSI tree to build the path
            while (current != root
                && current.parent != null) {
                when (current) {
                    is YAMLKeyValue -> {
                        // Add the key name to the path
                        current.keyText.let { keyText ->
                            pathSegments.add(0, keyText)
                        }
                    }
                    is YAMLMapping -> {
                        // We're inside a mapping, continue walking up
                    }
                }
                current = current.parent
            }

            return pathSegments
        }
    }
} 