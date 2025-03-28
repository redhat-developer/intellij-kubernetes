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
package com.redhat.devtools.intellij.kubernetes.editor.inlay.selector

import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.areMatchingMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.areMatchingMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getTemplate
import com.redhat.devtools.intellij.kubernetes.editor.util.hasMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.hasMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.hasSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.isCronJob
import com.redhat.devtools.intellij.kubernetes.editor.util.isDaemonSet
import com.redhat.devtools.intellij.kubernetes.editor.util.isDeployment
import com.redhat.devtools.intellij.kubernetes.editor.util.isJob
import com.redhat.devtools.intellij.kubernetes.editor.util.isNetworkPolicy
import com.redhat.devtools.intellij.kubernetes.editor.util.isPersistentVolume
import com.redhat.devtools.intellij.kubernetes.editor.util.isPersistentVolumeClaim
import com.redhat.devtools.intellij.kubernetes.editor.util.isPod
import com.redhat.devtools.intellij.kubernetes.editor.util.isPodDisruptionBudget
import com.redhat.devtools.intellij.kubernetes.editor.util.isReplicaSet
import com.redhat.devtools.intellij.kubernetes.editor.util.isService
import com.redhat.devtools.intellij.kubernetes.editor.util.isStatefulSet

class SelectorFilter(private val selectingElement: PsiElement) {

    private val selectingElementType: KubernetesTypeInfo? by lazy {
        selectingElement.getKubernetesTypeInfo()
    }

    private val hasSelector: Boolean by lazy {
        selectingElement.hasSelector()
    }

    private val hasMatchLabels: Boolean by lazy {
        selectingElement.hasMatchLabels()
    }

    private val hasMatchExpressions: Boolean by lazy {
        selectingElement.hasMatchExpressions()
    }

    fun filterMatching(toMatch: Collection<PsiElement>): Collection<PsiElement> {
        if (!hasSelector) {
            return emptyList()
        }
        return toMatch
            .filter(::isMatching)
    }

    fun isMatching(element: PsiElement): Boolean {
        if (selectingElementType == null) {
            return false
        }

        val selectableType = element.getKubernetesTypeInfo() ?: return false
        if (!isSelectable(selectableType)) {
            return false
        }

        val labels = getLabels(selectableType, element, selectingElementType) ?: return false

        return when {
            hasMatchLabels && hasMatchExpressions ->
                selectingElement.areMatchingMatchLabels(labels)
                        && selectingElement.areMatchingMatchExpressions(labels)

            hasMatchLabels ->
                selectingElement.areMatchingMatchLabels(labels)

            hasMatchExpressions ->
                selectingElement.areMatchingMatchExpressions(labels)

            else -> false
        }
    }

    private fun isSelectable(selectableType: KubernetesTypeInfo): Boolean {
        val selectingElementType = this.selectingElementType ?: return false
        return when {
            selectingElementType.isDeployment() ->
                selectableType.isPod()
                || selectableType.isDeployment() // can select deployment template

            selectingElementType.isCronJob() ->
                selectableType.isPod()
                || selectableType.isCronJob() // template

            selectingElementType.isDaemonSet() ->
                selectableType.isPod()
                || selectableType.isDaemonSet() // template

            selectingElementType.isJob() ->
                selectableType.isPod()
                || selectableType.isJob() // template

            selectingElementType.isReplicaSet() ->
                selectableType.isPod()
                || selectableType.isReplicaSet() // template

            selectingElementType.isStatefulSet() ->
                selectableType.isPod()
                || selectableType.isStatefulSet() // template

            selectingElementType.isNetworkPolicy()
            || selectingElementType.isPodDisruptionBudget()
            || selectingElementType.isService() ->
                selectableType.isPod()

            selectingElementType.isPersistentVolumeClaim() ->
                selectableType.isPersistentVolume()
                || selectableType.isPersistentVolumeClaim()

            else ->
                false
        }
    }

    private fun getLabels(
        selectableType: KubernetesTypeInfo,
        selectableElement: PsiElement,
        selectingElementType: KubernetesTypeInfo?
    ): PsiElement? {
        return when {
            selectingElementType == null ->
                null

            (selectingElementType.isCronJob()
                    && selectableType.isCronJob())
            || (selectingElementType.isDaemonSet()
                    && selectableType.isDaemonSet())
            || (selectingElementType.isDeployment()
                    && selectableType.isDeployment())
            || (selectingElementType.isJob()
                    && selectableType.isJob())
            || (selectingElementType.isReplicaSet()
                    && selectableType.isReplicaSet())
            || (selectingElementType.isStatefulSet()
                    && selectableType.isStatefulSet()) ->
                selectableElement.getTemplate()

            else ->
                selectableElement.getLabels()
        }
    }
}