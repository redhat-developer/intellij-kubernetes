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
import com.redhat.devtools.intellij.kubernetes.editor.util.areMatchingMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.areMatchingMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getJobTemplate
import com.redhat.devtools.intellij.kubernetes.editor.util.getKubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getResource
import com.redhat.devtools.intellij.kubernetes.editor.util.getTemplateLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.hasMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.hasMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.hasSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.hasTemplateLabels
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
import com.redhat.devtools.intellij.kubernetes.editor.util.isReplicationController
import com.redhat.devtools.intellij.kubernetes.editor.util.isService
import com.redhat.devtools.intellij.kubernetes.editor.util.isStatefulSet

/**
 * A filter that accepts labels, that are matching a given selector.
 */
class LabelsFilter(selector: PsiElement): PsiElementMappingsFilter {

    private val selectorResource: PsiElement? by lazy {
        selector.getResource()
    }

    private val selectorResourceType: KubernetesTypeInfo? by lazy {
        selectorResource?.getKubernetesTypeInfo()
    }

    private val hasSelector: Boolean by lazy {
        selectorResource?.hasSelector() ?: false
    }

    private  val hasMatchLabels: Boolean by lazy {
        selectorResource?.hasMatchLabels() ?: false
    }

    private val hasMatchExpressions: Boolean by lazy {
        selectorResource?.hasMatchExpressions() ?: false
    }

    override fun isAccepted(toAccept: PsiElement): Boolean {
        if (selectorResourceType == null
            || !hasSelector) {
            return false
        }

        val labeledResourceType = toAccept.getKubernetesTypeInfo() ?: return false
        if (!canSelect(labeledResourceType)) {
            return false
        }

        val labels = getLabels(labeledResourceType, toAccept, selectorResourceType) ?: return false
        val selectorResource = selectorResource ?: return false
        return when {
            hasMatchLabels && hasMatchExpressions ->
                selectorResource.areMatchingMatchLabels(labels)
                        && selectorResource.areMatchingMatchExpressions(labels)

            hasMatchLabels ->
                selectorResource.areMatchingMatchLabels(labels)

            hasMatchExpressions ->
                selectorResource.areMatchingMatchExpressions(labels)

            else -> false
        }
    }

    private fun canSelect(type: KubernetesTypeInfo): Boolean {
        val selectorType = selectorResourceType ?: return false
        return when {
            selectorType.isDaemonSet()
                    || selectorType.isDeployment()
                    || selectorType.isJob()
                    || selectorType.isNetworkPolicy()
                    || selectorType.isPodDisruptionBudget()
                    || selectorType.isReplicaSet()
                    || selectorType.isReplicationController()
                    || selectorType.isService()
                    || selectorType.isStatefulSet() ->
                type.isPod()
                        || hasPodTemplate(type)

            selectorType.isPersistentVolumeClaim() ->
                type.isPersistentVolume()

            else ->
                false
        }
    }

    private fun hasPodTemplate(type: KubernetesTypeInfo): Boolean {
        return type.isCronJob()
                || type.isDaemonSet()
                || type.isDeployment()
                || type.isJob()
                || type.isReplicaSet()
                || type.isStatefulSet()
    }

    override fun getMatchingElement(element: PsiElement): PsiElement? {
        val labeledType = element.getKubernetesTypeInfo() ?: return null
        return getLabels(labeledType, element, selectorResourceType)
    }

    private fun getLabels(
        labeledResourceType: KubernetesTypeInfo,
        labeledResource: PsiElement,
        selectorResourceType: KubernetesTypeInfo?
    ): PsiElement? {
        return when {
            selectorResourceType == null ->
                null

            labeledResourceType.isCronJob() ->
                labeledResource.getJobTemplate()?.getTemplateLabels()

            labeledResource.hasTemplateLabels() ->
                labeledResource.getTemplateLabels()

            else ->
                labeledResource.getLabels()
        }
    }
}