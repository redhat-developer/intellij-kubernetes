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
package com.redhat.devtools.intellij.kubernetes.usage

import com.intellij.json.psi.JsonObject
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getResource
import com.redhat.devtools.intellij.kubernetes.editor.util.getResourceName
import com.redhat.devtools.intellij.kubernetes.editor.util.isLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.isSelector
import org.jetbrains.yaml.psi.YAMLMapping

class SelectorDescriptionProvider: ElementDescriptionProvider {
    override fun getElementDescription(searchArgument: PsiElement, location: ElementDescriptionLocation): String? {
        return when (location) {
            is UsageViewTypeLocation ->
                getUsageViewTypeDescription(searchArgument)

            is UsageViewNodeTextLocation ->
                searchArgument.containingFile.virtualFile.name

            is UsageViewLongNameLocation ->
                "" // prevent default description provider from adding "selector"/"labels"

            is YAMLMapping ->
                "YAMLBlock"

            is JsonObject ->
                "JsonObject"


            else -> null
        }
    }

    private fun getUsageViewTypeDescription(element: PsiElement): String {
        val resource = element.getResource()
        val type = resource?.getKubernetesTypeInfo()?.kind
        val name = element.getResource()?.getResourceName()?.text
        return when {
            element.isSelector() ->
                "Labels matching selector in $type '$name'"
            element.isLabels() ->
                "Selectors matching labels in $type '$name'"
            else ->
                "Matching $type '$name'"
        }
    }
}