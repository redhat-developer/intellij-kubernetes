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

import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.hasLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.hasSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.hasTemplateLabels

/**
 * A filter that accepts selectors that are matching a given label
 */
class SelectorsFilter(private val labeledResource: PsiElement): PsiElementMappingsFilter {

    private val labeledResourceType: KubernetesTypeInfo? by lazy {
        labeledResource.getKubernetesTypeInfo()
    }

    private val hasLabels: Boolean by lazy {
        labeledResource.hasLabels()
                || labeledResource.hasTemplateLabels()
    }

    override fun getMatchingElement(element: PsiElement): PsiElement? {
        return element.getSelector()
    }

    override fun isAccepted(toAccept: PsiElement): Boolean {
        if (labeledResourceType == null
            || !hasLabels
            || !toAccept.hasSelector()) {
            return false
        }
        return LabelsFilter(toAccept)
            .isAccepted(labeledResource)
    }

}