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
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createKeyValueFor
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createLabelsFor
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
    fun `#getMatchLabels should return matchLabels if labels exist`() {
        // given
        val matching = createKeyValueFor("princess", "leia")
        val matchLabels = createLabelsFor(listOf(matching))
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return matchLabels if labels exist as direct children to selector`() {
        // given
        val matching = createKeyValueFor("c-3p0", "droid")
        val matchLabels = createLabelsFor(listOf(matching))
        createSelectorFor(matchLabels, yamlElement)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return empty matchLabels if list of labels is empty`() {
        // given
        val matchLabels = createLabelsFor(emptyList())
        createSelectorFor(matchLabels, yamlElement) // no intermediary matchLabels key

        // when
        val isEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(isEmpty!!.keyValues).isEmpty()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist`() {
        // given
        val matching = createKeyValueFor("jedi", "yoda")
        val matchLabels = createLabelsFor(listOf(matching))
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist as direct children of selector`() {
        // given
        val matching = createKeyValueFor("jedi", "yoda")
        val matchLabels = createLabelsFor(listOf(matching))
        createSelectorFor(matchLabels, yamlElement) // no intermediary matchLabels key

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return false labels do not exist`() {
        // given
        val matchLabels = createLabelsFor(emptyList())
        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val doesNotHaveMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(doesNotHaveMatchLabels).isFalse()
    }

    @Test
    fun `#areMatchingMatchLabels returns true if all labels match`() {
        // given
        val label = createKeyValueFor("jedi", "yoda")
        val labels = createLabelsFor(listOf(label))

        val matching = createKeyValueFor("jedi", "yoda")
        val matchLabels = createLabelsFor(listOf(matching))

        createMatchLabelsFor(matchLabels, yamlElement)

        // when
        val areMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(areMatching).isTrue()
    }

    @Test
    fun `#areMatchingMatchLabels returns false if labels dont match because labels are missing additional matchLabel`() {
        // given
        val jedi = createKeyValueFor("jedi", "yoda")
        val labels = createLabelsFor(listOf(jedi))

        val matching = createKeyValueFor("jedi", "yoda")
        val isMissing = createKeyValueFor("obiwan", "yoda")
        val matchLabels = createLabelsFor(listOf(matching, isMissing))

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

        val matching = createKeyValueFor("yoda", "goblin")
        val irrelevant = createKeyValueFor("obiwan", "jedi")
        val labels = createLabelsFor(listOf(matching, irrelevant))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match In-expression in key and quoted value`() {
        // given
        val expression = createYAMLExpression("yoda", "In", listOf("\"jedi\""))

        val matching = createKeyValueFor("yoda", "jedi")
        val labels = createLabelsFor(listOf(matching))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels match In-expression in key but not in value`() {
        // given
        val expression = createYAMLExpression("yoda", "In", listOf("jedi"))

        val differsInValue = createKeyValueFor("yoda", "goblin")
        val labels = createLabelsFor(listOf(differsInValue))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels do not match In-expression in key`() {
        // given
        val expression = createYAMLExpression("obiwan", "In", listOf("jedi"))

        val differsInKey = createKeyValueFor("yoda", "jedi")
        val labels = createLabelsFor(listOf(differsInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false empty labels are checked against In-expression`() {
        // given
        val expression = createYAMLExpression("obiwan", "In", listOf("jedi"))
        val emptyLabels = createLabelsFor(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match NotIn-expression in key but not in value`() {
        // given
        val expression = createYAMLExpression("yoda", "NotIn", listOf("jedi"))

        val yoda = createKeyValueFor("yoda", "jedi")
        val matchesExpression = createLabelsFor(listOf(yoda))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true because empty labels are missing match NotIn-expression`() {
        // given
        val expression = createYAMLExpression("yoda", "NotIn", listOf("jedi"))
        val emptyLabels = createLabelsFor(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels match Exists-expression by key with arbitrary value`() {
        // given
        val expression = createYAMLExpression("yoda", "Exists", emptyList())
        val matchesInKey = createKeyValueFor("yoda", "jedi")
        val matchesExpression = createLabelsFor(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false if labels do not match Exists-expression by key`() {
        // given
        val expression = createYAMLExpression("yoda", "Exists", emptyList())
        val matchesInKey = createKeyValueFor("r2d2", "android")
        val matchesExpression = createLabelsFor(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpressions returns true if labels are missing key of DoesNotExist-expression`() {
        // given
        val expression = createYAMLExpression("yoda", "DoesNotExist", emptyList())
        val isMissingKey = createKeyValueFor("r2d2", "android")
        val matchesExpression = createLabelsFor(listOf(isMissingKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpressions returns false have key that should DoesNotExist`() {
        // given
        val expression = createYAMLExpression("yoda", "DoesNotExist", emptyList())
        val matchesInKey = createKeyValueFor("yoda", "jedi")
        val matchesExpression = createLabelsFor(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }
}