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
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.json.psi.JsonObject
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLValue

private const val KEY_MATCH_LABELS = "matchLabels"
private const val KEY_MATCH_EXPRESSIONS = "matchExpressions"

private const val KEY_KEY = "key"
private const val KEY_OPERATOR = "operator"
private enum class OPERATORS { In, NotIn, Exists, DoesNotExist }
private const val KEY_VALUES = "values"

fun PsiElement.hasMatchExpressions(): Boolean {
    return when(this) {
        is YAMLMapping -> true == this.getMatchExpressions()?.items?.isNotEmpty()
        is JsonObject -> true == this.getMatchExpressions()?.propertyList?.isNotEmpty()
        else -> false
    }
}

fun YAMLMapping.getMatchExpressions(): YAMLSequence? {
    return this.getSelector()
        ?.getKeyValueByKey(KEY_MATCH_EXPRESSIONS)
        ?.value as? YAMLSequence?
}

fun JsonObject.getMatchExpressions(): JsonObject? {
    return this.getSelector()
        ?.findProperty(KEY_MATCH_EXPRESSIONS)
        ?.value as? JsonObject?
}

fun PsiElement.areMatchingMatchLabels(labels: PsiElement): Boolean {
    return when(this) {
        is YAMLMapping -> {
            val yamlLabels = labels as? YAMLMapping ?: return false
            this.areMatchingMatchLabels(yamlLabels)
        }
        is JsonObject ->
            false
        else ->
            false
    }
}

private fun YAMLMapping.areMatchingMatchLabels(labels: YAMLMapping): Boolean {
    val matchLabels = this.getMatchLabels() ?: return false
    return matchLabels.keyValues.all { matchLabel ->
        this.isMatchingMatchLabel(matchLabel, labels)
    }
}

private fun YAMLMapping.isMatchingMatchLabel(matchLabel: YAMLKeyValue, labels: YAMLMapping): Boolean {
    val labelName = matchLabel.keyText
    val labelValue = matchLabel.valueText
    val matching = labels.keyValues.find { it.keyText == labelName } ?: return false
    return matching.valueText == labelValue
}

fun PsiElement.areMatchingMatchExpressions(labels: PsiElement): Boolean {
    return when(this) {
        is YAMLMapping -> {
            val yamlLabels = labels as? YAMLMapping? ?: return false
            this.areMatchingMatchExpressions(yamlLabels)
        }
        is JsonObject ->
            false
        else ->
            false
    }
}

private fun YAMLMapping.areMatchingMatchExpressions(labels: YAMLMapping): Boolean {
    val expressions = this.getMatchExpressions() ?: return false
    return expressions.items.all { expression ->
        expression.isMatchingMatchExpression(labels)
    }
}

fun YAMLSequenceItem.isMatchingMatchExpression(labels: YAMLMapping): Boolean {
    val expression = value as? YAMLMapping ?: return false
    val key = expression.getKeyValueByKey(KEY_KEY)?.valueText ?: return false
    val operator = expression.getKeyValueByKey(KEY_OPERATOR)?.valueText
    val values = expression.getKeyValueByKey(KEY_VALUES)?.value as? YAMLSequence

    val label = labels.getKeyValueByKey(key) // key label in expression not found in element labels

    return when (operator) {
        OPERATORS.In.name -> {
            label != null
                    && values != null
                    && values.items.any {
                StringUtil.unquoteString(it.text) == StringUtil.unquoteString(label.valueText)
            }
        }

        OPERATORS.NotIn.name -> {
            label == null
                    || values == null
                    || values.items.any {
                StringUtil.unquoteString(it.text) == StringUtil.unquoteString(label.valueText)
            }
        }

        OPERATORS.Exists.name ->
            label != null

        OPERATORS.DoesNotExist.name ->
            label == null

        else ->
            false
    }
}

fun PsiElement.hasMatchLabels(): Boolean {
    return when(this) {
        is YAMLMapping -> true == this.getMatchLabels()?.keyValues?.isNotEmpty()
        is JsonObject -> true == this.getMatchLabels()?.propertyList?.isNotEmpty()
        else -> false
    }
}

fun YAMLMapping.getMatchLabels(): YAMLMapping? {
    val selector = this.getSelector() ?: return null
    val matchLabels = selector.getKeyValueByKey(KEY_MATCH_LABELS)
    return if (matchLabels != null) {
        matchLabels.value as? YAMLMapping?
    } else if (selector.getKeyValueByKey(KEY_MATCH_EXPRESSIONS) == null) {
        // Service can have matchLabels as direct children without the 'matchLabels' key.
        // check if selector has matchExpressions which are not matchLabels
        selector
    } else {
        null
    }
}

private fun YAMLValue.isYamlStringValue(): Boolean {
    return this is YAMLScalar && this.textValue != null
}


private fun JsonObject.getMatchLabels(): JsonObject? {
    val selector = this.getSelector() ?: return null
    val matchLabels = selector.findProperty(KEY_MATCH_LABELS)
    return if (matchLabels != null) {
        matchLabels.value as? JsonObject?
    } else {
        // Service can have matchLabels as direct children without the 'matchLabels' key.
        selector
    }
}
