package com.redhat.devtools.intellij.kubernetes.editor.inlay.selector

import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getKind
import com.redhat.devtools.intellij.kubernetes.editor.util.getMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.getMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getMetadataName
import com.redhat.devtools.intellij.kubernetes.editor.util.hasKindAndName
import com.redhat.devtools.intellij.kubernetes.editor.util.isMatchingExpressions
import com.redhat.devtools.intellij.kubernetes.editor.util.isMatchingLabels
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

object SelectorPresentations {

    fun create(element: PsiElement, root: PsiElement, info: KubernetesTypeInfo, sink: InlayHintsSink, editor: Editor): Collection<InlayPresentation>? {
        if (element is YAMLMapping) {
            findMatchingResources(element, root)
        }
        return emptyList()
    }

    private fun findMatchingResources(selectorResource: YAMLMapping, root: PsiElement): List<MatchingResource> {
        val matchLabels = selectorResource.getMatchLabels()
        val matchExpressions = selectorResource.getMatchExpressions()
        val allResources = PsiTreeUtil.findChildrenOfType(root, YAMLMapping::class.java)
            .filter { it != selectorResource // dont match yourself
                it.hasKindAndName() }

        return allResources
            .filter { resource ->
                resource.isMatchingLabels(matchLabels)
                        && resource.isMatchingExpressions(matchExpressions)
            }
            .map { resource ->
                val kind = resource.getKind() ?: return emptyList()
                val name = resource.getMetadataName() ?: return emptyList()
                MatchingResource(kind, name, resource)
            }
    }

    private data class Resource(
        val name: String?,
        val kind: String?,
        val labels: Map<String, String>,
        val element: YAMLMapping
    ) {
        fun findMatchLabels(): List<Triple<String, String, YAMLKeyValue>> {
            return element.getMatchLabels()?.keyValues?.map { keyValue ->
                    Triple(keyValue.keyText, keyValue.valueText, keyValue)
            }
            ?: emptyList()
        }
    }

    private data class MatchExpression(
        val key: String,
        val operator: String,
        val values: List<String>,
        val element: YAMLMapping
    )

    private data class MatchingResource(val kind: String, val name: String, val element: PsiElement)

}