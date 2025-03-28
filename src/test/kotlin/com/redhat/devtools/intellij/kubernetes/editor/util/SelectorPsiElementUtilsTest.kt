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

import com.nhaarman.mockitokotlin2.mock
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchExpressionsFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchLabelsFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelectorFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequence
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLExpression
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Test

class PsiElementExtensionsTest {

    private val yamlElement = mock<YAMLMapping>()

    @Test
    fun `#getLabels returns labels if exist`() {
        // given
        val yamlMapping = mock<YAMLMapping>()
        createLabels(yamlMapping, yamlElement)

        // when
        val labels = yamlElement.getLabels()

        // then
        assertThat(labels).isEqualTo(yamlMapping)
    }

    @Test
    fun `#getLabels returns null if labels dont exist`() {
        // given no labels are created
        // when
        val labels = yamlElement.getLabels()

        // then
        assertThat(labels).isNull()
    }

    @Test
    fun `#getMatchLabels should return matchLabels if labels exist`() {
        // given
        val matching = createKeyValue("princess", "leia")
        val matchLabels = createLabels(listOf(matching))
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return matchLabels if labels exist as direct children to selector`() {
        // given
        val matching = createKeyValue("c-3p0", "droid")
        val matchLabels = createLabels(listOf(matching))
        createSelectorFor(matchLabels, yamlElement)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return empty matchLabels if list of labels is empty`() {
        // given
        val matchLabels = createLabels(emptyList())
        createSelectorFor(matchLabels, yamlElement) // no intermediary matchLabels key

        // when
        val isEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(isEmpty!!.keyValues).isEmpty()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist`() {
        // given
        val matching = createKeyValue("jedi", "yoda")
        val matchLabels = createLabels(listOf(matching))
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist as direct children of selector`() {
        // given
        val matching = createKeyValue("jedi", "yoda")
        val matchLabels = createLabels(listOf(matching))
        createSelectorFor(matchLabels, yamlElement) // no intermediary matchLabels key

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return false labels do not exist`() {
        // given
        val matchLabels = createLabels(emptyList())
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val doesNotHaveMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(doesNotHaveMatchLabels).isFalse()
    }

    @Test
    fun `#areMatchingMatchLabels returns true if all labels match`() {
        // given
        val label = createKeyValue("jedi", "yoda")
        val labels = createLabels(listOf(label))

        val matching = createKeyValue("jedi", "yoda")
        val matchLabels = createLabels(listOf(matching))

        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val areMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(areMatching).isTrue()
    }

    @Test
    fun `#areMatchingMatchLabels returns false if labels dont match because labels are missing additional matchLabel`() {
        // given
        val jedi = createKeyValue("jedi", "yoda")
        val labels = createLabels(listOf(jedi))

        val matching = createKeyValue("jedi", "yoda")
        val isMissing = createKeyValue("obiwan", "yoda")
        val matchLabels = createLabels(listOf(matching, isMissing))

        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val isMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#getMatchExpressions returns matchExpressions if exist`() {
        // given
        val empty = createYAMLSequence(emptyList())
        val matchExpressions = createMatchExpressionsFor(empty, yamlElement)

        // when
        val found = yamlElement.getMatchExpressions()

        // then
        assertThat(found).isEqualTo(matchExpressions.value)
    }

    @Test
    fun `#hasMatchExpressions returns false if match expressions are empty`() {
        // given
        val empty = createYAMLSequence(emptyList())
        createMatchExpressionsFor(empty, yamlElement)

        // when
        val hasMatchExpressions = yamlElement.hasMatchExpressions()

        // then
        assertThat(hasMatchExpressions).isFalse()
    }

    @Test
    fun `#hasMatchExpressions returns true if match expressions exist`() {
        // given
        val nonEmpty = createYAMLSequence(listOf(mock()))
        createMatchExpressionsFor(nonEmpty, yamlElement)

        // when
        val hasMatchExpressions = yamlElement.hasMatchExpressions()

        // then
        assertThat(hasMatchExpressions).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match In-expression in key and value of alternatives`() {
        // given
        val expression = createYAMLExpression("yoda", "In", listOf("jedi", "goblin"))

        val matching = createKeyValue("yoda", "goblin")
        val irrelevant = createKeyValue("obiwan", "jedi")
        val labels = createLabels(listOf(matching, irrelevant))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match In-expression in key and quoted value`() {
        // given
        val expression = createYAMLExpression("yoda", "In", listOf("\"jedi\""))

        val matching = createKeyValue("yoda", "jedi")
        val labels = createLabels(listOf(matching))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels match In-expression in key but not in value`() {
        // given
        val expression = createYAMLExpression("yoda", "In", listOf("jedi"))

        val differsInValue = createKeyValue("yoda", "goblin")
        val labels = createLabels(listOf(differsInValue))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels do not match In-expression in key`() {
        // given
        val expression = createYAMLExpression("obiwan", "In", listOf("jedi"))

        val differsInKey = createKeyValue("yoda", "jedi")
        val labels = createLabels(listOf(differsInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false empty labels are checked against In-expression`() {
        // given
        val expression = createYAMLExpression("obiwan", "In", listOf("jedi"))
        val emptyLabels = createLabels(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match NotIn-expression in key but not in value`() {
        // given
        val expression = createYAMLExpression("yoda", "NotIn", listOf("jedi"))

        val yoda = createKeyValue("yoda", "jedi")
        val matchesExpression = createLabels(listOf(yoda))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true because empty labels are missing match NotIn-expression`() {
        // given
        val expression = createYAMLExpression("yoda", "NotIn", listOf("jedi"))
        val emptyLabels = createLabels(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match Exists-expression by key with arbitrary value`() {
        // given
        val expression = createYAMLExpression("yoda", "Exists", emptyList())
        val matchesInKey = createKeyValue("yoda", "jedi")
        val matchesExpression = createLabels(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels do not match Exists-expression by key`() {
        // given
        val expression = createYAMLExpression("yoda", "Exists", emptyList())
        val matchesInKey = createKeyValue("r2d2", "android")
        val matchesExpression = createLabels(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels are missing key of DoesNotExist-expression`() {
        // given
        val expression = createYAMLExpression("yoda", "DoesNotExist", emptyList())
        val isMissingKey = createKeyValue("r2d2", "android")
        val matchesExpression = createLabels(listOf(isMissingKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false have key that should DoesNotExist`() {
        // given
        val expression = createYAMLExpression("yoda", "DoesNotExist", emptyList())
        val matchesInKey = createKeyValue("yoda", "jedi")
        val matchesExpression = createLabels(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }
}