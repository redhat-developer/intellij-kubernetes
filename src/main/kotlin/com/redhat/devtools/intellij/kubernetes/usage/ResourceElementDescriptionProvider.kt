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

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getMetadata
import com.redhat.devtools.intellij.kubernetes.editor.util.getResourceName
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource

class ResourceElementDescriptionProvider: ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val type = element.getKubernetesTypeInfo() ?: return null

        return when (location) {
            is UsageViewTypeLocation ->
                "Matching ${type.kind} ${element.getResourceName()?.text ?: ""}"

//           is UsageViewLongNameLocation ->
//                "Matching ${type.kind} ${element.getResourceName()?.text ?: ""}"

//            is UsageViewShortNameLocation ->
//                //element.getResourceName()?.text
//                "blabla"

            is UsageViewNodeTextLocation ->
                element.containingFile.virtualFile.name
            else -> null
        }

    }
}