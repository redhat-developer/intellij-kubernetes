package com.redhat.devtools.intellij.kubernetes.editor.util

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.impl.YAMLMappingImpl

private const val KEY_KIND = "kind"
private const val KEY_METADATA = "metadata"
private const val KEY_NAME = "name"
private const val KEY_LABELS = "labels"
private const val KEY_SPEC = "spec"
private const val KEY_SELECTOR = "selector"
private const val KEY_MATCH_LABELS = "matchLabels"
private const val KEY_MATCH_EXPRESSIONS = "matchExpressions"
// match expression
private const val KEY_KEY = "matchExpressions"
private const val KEY_OPERATOR = "operator"
private enum class OPERATORS { In, NotIn, Exists, DoesNotExist }
private const val KEY_VALUES = "values"

fun YAMLMapping.isMatchingLabels(labels: YAMLMapping?): Boolean {
    if (labels == null) {
        return false
    }
    val labels = this.getLabels() ?: return false

    for (matchLabel in labels.keyValues) {
        val labelName = matchLabel.keyText
        val labelValue = (matchLabel.value as? YAMLKeyValue)?.valueText ?: matchLabel.valueText

        val resourceLabel = labels.keyValues.find { it.keyText == labelName }
        if (resourceLabel == null || resourceLabel.valueText != labelValue) {
            return false
        }
    }

    return true
}

fun YAMLMapping.isMatchingExpressions(expressions: YAMLSequence?): Boolean {
    if (expressions == null) {
        return true
    }
    return expressions.items.all { expression ->
        this.isMatchingExpression(expression)
    }
}

fun YAMLMapping.isMatchingExpression(item: YAMLSequenceItem?,): Boolean {
    val labels = this.getLabels() ?: return false

    if (item !is YAMLMapping) return true // ignore invalid yaml

    val key = item.getKeyValueByKey(KEY_KEY)?.value ?: return true // ignore expression without key
    val operator = item.getKeyValueByKey(KEY_OPERATOR)?.value ?: return true // ignore expression without operator
    val values = item.getKeyValueByKey(KEY_VALUES)?.value as? YAMLSequence

    val resourceLabel = labels.keyValues.find { it.keyText == key.text }

    return when (operator.text) {
        OPERATORS.In.name -> {
            resourceLabel == null
                    || values == null
                    || values.items.all {
                StringUtil.unquoteString(it.text) == StringUtil.unquoteString(resourceLabel.valueText)
            }
        }

        OPERATORS.NotIn.name -> {
            resourceLabel != null
                    && values != null
                    && values.items.none {
                StringUtil.unquoteString(it.text) == StringUtil.unquoteString(resourceLabel.valueText)
            }
        }

        OPERATORS.Exists.name -> {
            resourceLabel != null
        }

        OPERATORS.DoesNotExist.name -> {
            resourceLabel == null
        }

        else -> {
            false
        }
    }
}

fun YAMLMapping.getMatchLabels(): YAMLMapping? {
    val selector = this.getSelector() ?: return null
    val matchLabels = selector.getKeyValueByKey(KEY_MATCH_LABELS)
    return matchLabels?.value as YAMLMapping?
        ?: selector
}

fun YAMLMapping.getSelector(): YAMLMapping? {
    return (this.getKeyValueByKey(KEY_SPEC)?.value as? YAMLMappingImpl)
        ?.getKeyValueByKey(KEY_SELECTOR)?.value as? YAMLMapping
}

fun YAMLMapping.getMatchExpressions(): YAMLSequence? {
    return ((this.getKeyValueByKey(KEY_SPEC) as? YAMLMapping)
        ?.getKeyValueByKey(KEY_SELECTOR) as? YAMLMapping)
        ?.getKeyValueByKey(KEY_MATCH_EXPRESSIONS) as? YAMLSequence?
}

fun YAMLMapping.hasKindAndName(): Boolean {
    val kind = this.getKeyValueByKey(KEY_KIND)?.valueText
    val metadata = this.getMetadata()
    val name = metadata?.getKeyValueByKey(KEY_NAME)?.valueText
    return !kind.isNullOrEmpty() && !name.isNullOrEmpty()
}

fun YAMLMapping.getKind(): String? {
    return this.getKeyValueByKey(KEY_KIND)
        ?.valueText
}

fun YAMLMapping.getMetadataName(): String? {
    return this.getMetadata()
        ?.getKeyValueByKey(KEY_NAME)
        ?.valueText
}

fun YAMLMapping.getLabels(): YAMLMapping? {
    return this.getMetadata()
        ?.getKeyValueByKey(KEY_LABELS)
        ?.value as? YAMLMapping
}

fun YAMLMapping.getMetadata(): YAMLMapping? {
    return this.getKeyValueByKey(KEY_METADATA)
        ?.value as? YAMLMapping
}

fun JsonObject.hasKindAndName(): Boolean {
    val kind = this.findProperty(KEY_KIND)?.value as? JsonStringLiteral
    val metadata = this.getMetadata()
    val name = metadata?.findProperty(KEY_NAME)?.value as? JsonStringLiteral
    return kind != null && name != null
}

fun JsonObject.getKind(): String? {
    val kind = this.findProperty(KEY_KIND)?.value as? JsonStringLiteral
    return kind?.value
}

fun JsonObject.getMetadataName(): String? {
    val metadata = this.getMetadata()
    val name = metadata?.findProperty(KEY_NAME)?.value as? JsonStringLiteral
    return name?.value
}

fun JsonObject.getLabels(): JsonObject? {
    val metadata = this.getMetadata()
    return metadata
}

fun JsonObject.getMetadata(): JsonObject? {
    return this.findProperty(KEY_METADATA)?.value as? JsonObject
}

fun JsonObject.getMatchLabels(): JsonObject? {
    return ((this.findProperty(KEY_SPEC) as? JsonObject)
        ?.findProperty(KEY_SELECTOR) as? JsonObject)
        ?.findProperty(KEY_MATCH_LABELS) as? JsonObject
}

