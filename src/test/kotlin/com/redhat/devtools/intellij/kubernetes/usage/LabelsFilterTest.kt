/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.usage

import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelector
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createTemplate
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequenceItem
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequence
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test

class LabelsFilterTest {

    companion object {
        private const val LABEL_KEY = "droid"
        private const val LABEL_VALUE = "c-3p0"
    }

    private lateinit var filter: LabelsFilter

    private lateinit var hasMatchLabelsAndExpressions: YAMLMapping
    private lateinit var matchingPod: YAMLMapping
    private lateinit var matchingPodLabels: YAMLMapping

    @Before
    fun before() {
        this.hasMatchLabelsAndExpressions = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchLabelsAndExpressions.createSelector(
            createYAMLMapping(listOf(
                createYAMLKeyValue(
                    "matchLabels",
                    createYAMLMapping(listOf(createYAMLKeyValue(LABEL_KEY, LABEL_VALUE)))
                ),
                createYAMLKeyValue(
                    "matchExpressions",
                    createYAMLSequence(listOf(
                        createYAMLSequenceItem(LABEL_KEY, "In", listOf(LABEL_VALUE, "humanoid"))
                    ))
                ))
            )
        )

        this.matchingPod = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Pod")
        ))
        this.matchingPodLabels = matchingPod.createLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue(LABEL_KEY, LABEL_VALUE)
            ))
        ).value as YAMLMapping

        this.filter = LabelsFilter(hasMatchLabelsAndExpressions)
    }

    @Test
    fun `#isAccepted returns true if pod labels match all selector labels and expressions`() {
        // given
        // when
        val isAccepted = filter.isAccepted(matchingPod)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns true if pod labels match all selector expressions`() {
        // given
        val hasMatchExpressions = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchExpressions.createMatchExpressions(
            createYAMLSequence(listOf(
                createYAMLSequenceItem("jedi", "In", listOf("yoda", "obiwan", "luke")), // matches jedi/obiwan
                createYAMLSequenceItem("droid", "Exists", emptyList()) // matches droid/r2d2
            ))
        )

        val filter = LabelsFilter(hasMatchExpressions)
        val additionalLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Pod")
        ))
        additionalLabels.createLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue("jedi", "obiwan"),
                createYAMLKeyValue("droid", "r2d2")
            ))
        )
        // when
        val isAccepted = filter.isAccepted(additionalLabels)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns true if pod labels match all selector labels`() {
        // given
        val hasMatchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchLabels.createMatchLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue("jedi", "obiwan"),
                createYAMLKeyValue("droid", "r2d2")
            ))
        )

        val filter = LabelsFilter(hasMatchLabels)
        val additionalLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Pod")
        ))
        additionalLabels.createLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue("jedi", "obiwan"),
                createYAMLKeyValue("droid", "r2d2"),
                createYAMLKeyValue("sith", "darth vader")
            ))
        )
        // when
        val isAccepted = filter.isAccepted(additionalLabels)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns false if selecting deployment has no selector`() {
        // given
        val hasNoSelector = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))

        val filter = LabelsFilter(hasNoSelector)
        // when
        val isAccepted = filter.isAccepted(matchingPod)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns false if selecting element is no k8s resource`() {
        // given
        val isNoResource = createYAMLMapping(emptyList()) // missing kind, apiVersion

        val filter = LabelsFilter(isNoResource)
        // when
        val isAccepted = filter.isAccepted(matchingPod)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns false if filtered element is no k8s resource`() {
        // given
        val notAResource = createYAMLMapping(
            emptyList()
        )
        // when
        val isAccepted = filter.isAccepted(notAResource)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns false if deployment is selector and filtered element is neither deployment nor pod`() {
        // given
        val notPodNorDeployment = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Service") // only pod or deployment supported
        ))
        // when
        val isAccepted = filter.isAccepted(notPodNorDeployment)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns true if deployment is selector and filtered element is deployment with matching template labels`() {
        // given
        val matchingTemplateLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        matchingTemplateLabels.createTemplate(
            createYAMLMapping(listOf(
                createYAMLKeyValue(
                    "metadata",
                    createYAMLMapping(listOf(
                        createYAMLKeyValue(
                            "labels",
                            createYAMLMapping(listOf(
                                createYAMLKeyValue(LABEL_KEY, LABEL_VALUE) // matching labels in spec>template
                            ))
                        )
                    ))
                )
            ))
        )
        // when
        val isAccepted = filter.isAccepted(matchingTemplateLabels)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#getMatchingElement returns pod labels that match given deployment selector`() {
        // given
        // when
        val matchingElement = filter.getMatchingElement(matchingPod)
        // then
        assertThat(matchingElement).isEqualTo(matchingPodLabels)
    }

    @Test
    fun `#getMatchingElement returns deployment template labels that match given deployment selector`() {
        // given
        val matchingTemplateLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        val templateLabels = createYAMLMapping(listOf(
            createYAMLKeyValue(LABEL_KEY, LABEL_VALUE) // matching labels in spec>template
        ))
        matchingTemplateLabels.createTemplate(
            createYAMLMapping(listOf(
                createYAMLKeyValue(
                    "metadata",
                    createYAMLMapping(listOf(
                        createYAMLKeyValue(
                            "labels",
                            templateLabels
                        )
                    ))
                )
            ))
        )
        // when
        val matchingElement = filter.getMatchingElement(matchingTemplateLabels)
        // then
        assertThat(matchingElement).isEqualTo(templateLabels)
    }
}
