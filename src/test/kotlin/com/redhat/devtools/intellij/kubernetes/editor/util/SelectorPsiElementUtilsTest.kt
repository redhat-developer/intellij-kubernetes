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

import org.mockito.kotlin.mock
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelector
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequenceItem
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequence
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Test

class PsiElementExtensionsTest {

    private val yamlElement = mock<YAMLMapping>()

    @Test
    fun `#getMatchLabels should return matchLabels if labels exist`() {
        // given
        val matching = createYAMLKeyValue("princess", "leia")
        val matchLabels = createYAMLMapping(listOf(matching))
        yamlElement.createMatchLabels(matchLabels)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return matchLabels if labels exist as direct children to selector`() {
        // given
        val matching = createYAMLKeyValue("c-3p0", "droid")
        val matchLabels = createYAMLMapping(listOf(matching))
        yamlElement.createSelector(matchLabels)

        // when
        val nonEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(nonEmpty).isSameAs(matchLabels)
    }

    @Test
    fun `#getMatchLabels should return empty matchLabels if list of labels is empty`() {
        // given
        val matchLabels = createYAMLMapping(emptyList())
        yamlElement.createSelector(matchLabels) // no intermediary matchLabels key

        // when
        val isEmpty = yamlElement.getMatchLabels()

        // then
        assertThat(isEmpty!!.keyValues).isEmpty()
    }

    @Test
    fun `#getMatchLabels should return null if selector only has matchExpressions`() {
        // given
        val hasMatchExpressions = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchExpressions.createMatchExpressions(
            createYAMLSequence(listOf(
                createYAMLSequenceItem("jedi", "In", listOf("yoda", "obiwan", "luke"))
            ))
        )
        // when
        val isNull = yamlElement.getMatchLabels()

        // then
        assertThat(isNull).isNull()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist`() {
        // given
        val matching = createYAMLKeyValue("jedi", "yoda")
        val matchLabels = createYAMLMapping(listOf(matching))
        yamlElement.createMatchLabels(matchLabels)

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return true when labels exist as direct children of selector`() {
        // given
        val matching = createYAMLKeyValue("jedi", "yoda")
        val matchLabels = createYAMLMapping(listOf(matching))
        yamlElement.createSelector(matchLabels) // no intermediary matchLabels key

        // when
        val hasMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(hasMatchLabels).isTrue()
    }

    @Test
    fun `#hasMatchLabels should return false labels do not exist`() {
        // given
        val matchLabels = createYAMLMapping(emptyList())
        yamlElement.createMatchLabels(matchLabels)

        // when
        val doesNotHaveMatchLabels = yamlElement.hasMatchLabels()

        // then
        assertThat(doesNotHaveMatchLabels).isFalse()
    }

    @Test
    fun `#areMatchingMatchLabels returns true if all labels match`() {
        // given
        val labels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "yoda")
        ))

        val matchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "yoda")
        ))

        yamlElement.createMatchLabels(matchLabels)

        // when
        val areMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(areMatching).isTrue()
    }

    @Test
    fun `#areMatchingMatchLabels returns false if not all required by matchLabels exist in labels`() {
        // given
        val labels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "yoda")
        ))

        val matchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "yoda"),
            createYAMLKeyValue("obiwan", "yoda") // missing from labels
        ))

        yamlElement.createMatchLabels(matchLabels)

        // when
        val isMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#areMatchingMatchLabels returns true if matchLabels are empty`() {
        // given
        val labels = createYAMLMapping(listOf(
            createYAMLKeyValue("jedi", "yoda")
        ))

        val matchLabels = createYAMLMapping(emptyList())

        yamlElement.createMatchLabels(matchLabels)

        // when
        val isMatching = yamlElement.areMatchingMatchLabels(labels)

        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#getMatchExpressions returns matchExpressions if exist`() {
        // given
        val empty = createYAMLSequence(emptyList())
        val matchExpressions = yamlElement.createMatchExpressions(empty)

        // when
        val found = yamlElement.getMatchExpressions()

        // then
        assertThat(found).isEqualTo(matchExpressions.value)
    }

    @Test
    fun `#hasMatchExpressions returns false if match expressions are empty`() {
        // given
        val empty = createYAMLSequence(emptyList())
        yamlElement.createMatchExpressions(empty)

        // when
        val hasMatchExpressions = yamlElement.hasMatchExpressions()

        // then
        assertThat(hasMatchExpressions).isFalse()
    }

    @Test
    fun `#hasMatchExpressions returns true if match expressions exist`() {
        // given
        val nonEmpty = createYAMLSequence(listOf(mock()))
        yamlElement.createMatchExpressions(nonEmpty)

        // when
        val hasMatchExpressions = yamlElement.hasMatchExpressions()

        // then
        assertThat(hasMatchExpressions).isTrue()
    }

    @Test
    fun `isMatchingMatchExpression returns true if labels match In-expression in key and value of alternatives`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "In", listOf("jedi", "goblin"))

        val matching = createYAMLKeyValue("yoda", "goblin")
        val irrelevant = createYAMLKeyValue("obiwan", "jedi")
        val labels = createYAMLMapping(listOf(matching, irrelevant))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `isMatchingMatchExpression returns true if labels match In-expression in key and quoted value`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "In", listOf("\"jedi\""))

        val matching = createYAMLKeyValue("yoda", "jedi")
        val labels = createYAMLMapping(listOf(matching))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `isMatchingMatchExpression returns false if labels match In-expression in key but not in value`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "In", listOf("jedi"))

        val differsInValue = createYAMLKeyValue("yoda", "goblin")
        val labels = createYAMLMapping(listOf(differsInValue))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `isMatchingMatchExpression returns false if labels do not match In-expression in key`() {
        // given
        val expression = createYAMLSequenceItem("obiwan", "In", listOf("jedi"))

        val differsInKey = createYAMLKeyValue("yoda", "jedi")
        val labels = createYAMLMapping(listOf(differsInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(labels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `isMatchingMatchExpression returns false empty labels are checked against In-expression`() {
        // given
        val expression = createYAMLSequenceItem("obiwan", "In", listOf("jedi"))
        val emptyLabels = createYAMLMapping(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#isMatchingMatchExpression returns true if labels match NotIn-expression in key but not in value`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "NotIn", listOf("jedi"))

        val yoda = createYAMLKeyValue("yoda", "jedi")
        val matchesExpression = createYAMLMapping(listOf(yoda))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpression returns true because empty labels are missing match NotIn-expression`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "NotIn", listOf("jedi"))
        val emptyLabels = createYAMLMapping(emptyList())

        // when
        val isMatching = expression.isMatchingMatchExpression(emptyLabels)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpression returns true if labels match Exists-expression by key with arbitrary value`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "Exists", emptyList())
        val matchesInKey = createYAMLKeyValue("yoda", "jedi")
        val matchesExpression = createYAMLMapping(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#isMatchingMatchExpression returns false if labels do not match Exists-expression by key`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "Exists", emptyList())
        val matchesInKey = createYAMLKeyValue("r2d2", "android")
        val matchesExpression = createYAMLMapping(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `isMatchingMatchExpression returns true if labels are missing key of DoesNotExist-expression`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "DoesNotExist", emptyList())
        val isMissingKey = createYAMLKeyValue("r2d2", "android")
        val matchesExpression = createYAMLMapping(listOf(isMissingKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `isMatchingMatchExpression returns false have key that should DoesNotExist`() {
        // given
        val expression = createYAMLSequenceItem("yoda", "DoesNotExist", emptyList())
        val matchesInKey = createYAMLKeyValue("yoda", "jedi") // value should be "green"
        val matchesExpression = createYAMLMapping(listOf(matchesInKey))

        // when
        val isMatching = expression.isMatchingMatchExpression(matchesExpression)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#areMatchingMatchExpressions returns true if labels are matching all match expressions`() {
        // given
        yamlElement.createMatchExpressions(
            createYAMLSequence(listOf(
                createYAMLSequenceItem("yoda", "Exists", emptyList()),
                createYAMLSequenceItem("yoda", "In", listOf("green"))
            ))
        )

        val matchesAllExpressions = createYAMLMapping(listOf(
            createYAMLKeyValue("yoda", "green")
        ))

        // when
        val isMatching = yamlElement.areMatchingMatchExpressions(matchesAllExpressions)
        // then
        assertThat(isMatching).isTrue()
    }

    @Test
    fun `#areMatchingMatchExpressions returns false if labels DO NOT match all match expressions`() {
        // given
        yamlElement.createMatchExpressions(
            createYAMLSequence(listOf(
                    createYAMLSequenceItem("yoda", "Exists", emptyList()),
                    createYAMLSequenceItem("yoda", "In", listOf("green"))
            ))
        )
        val doesNotMatchInExpression = createYAMLMapping(listOf(
            createYAMLKeyValue("yoda", "jedi")
        ))

        // when
        val isMatching = yamlElement.areMatchingMatchExpressions(doesNotMatchInExpression)
        // then
        assertThat(isMatching).isFalse()
    }

    @Test
    fun `#areMatchingMatchExpressions returns true if match expressions are empty`() {
        // given
        yamlElement.createMatchExpressions(
            createYAMLSequence(emptyList()) // no match expressions, only key
        )
        val matchingLabels = createYAMLMapping(
            listOf(createYAMLKeyValue("obiwan", "jedi"))
        )

        // when
        val isMatching = yamlElement.areMatchingMatchExpressions(matchingLabels)
        // then
        assertThat(isMatching).isTrue()
    }
}