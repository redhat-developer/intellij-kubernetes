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

import com.redhat.devtools.intellij.kubernetes.editor.mocks.createLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchExpressions
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createMatchLabels
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createSelector
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createTemplate
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLKeyValue
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLMapping
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequence
import com.redhat.devtools.intellij.kubernetes.editor.mocks.createYAMLSequenceItem
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelector
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.psi.YAMLMapping
import org.junit.Before
import org.junit.Test

class SelectorsFilterTest {

    companion object {
        private const val LABEL_KEY = "princess"
        private const val LABEL_VALUE = "leia"
    }

    private lateinit var filter: SelectorsFilter

    private lateinit var pod: YAMLMapping
    private lateinit var deploymentMatchingPod: YAMLMapping

    @Before
    fun before() {
        this.pod = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Pod")
        ))
        pod.createLabels(
            createYAMLMapping(listOf(createYAMLKeyValue(LABEL_KEY, LABEL_VALUE)))
        )

        this.deploymentMatchingPod = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        this.deploymentMatchingPod.createSelector(
            createYAMLMapping(listOf(
                createYAMLKeyValue(
                    "matchLabels",
                    createYAMLMapping(listOf(
                        createYAMLKeyValue(
                            LABEL_KEY,
                            LABEL_VALUE
                        ))
                    )
                ),
                createYAMLKeyValue(
                    "matchExpressions",
                    createYAMLSequence(listOf(
                        createYAMLSequenceItem(LABEL_KEY, "In", listOf(LABEL_VALUE, "humanoid"))
                    ))
                ))
            )
        )

        this.filter = SelectorsFilter(pod)
    }

    @Test
    fun `#isAccepted returns true if filtered element labels matching all selector labels and expressions`() {
        // given
        // when
        val isAccepted = filter.isAccepted(deploymentMatchingPod)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns true if all selector expressions match pod labels`() {
        // given
        val additionalLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Pod")
        ))
        additionalLabels.createLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue("jedi", "obiwan"),
                createYAMLKeyValue("droid", "r2d2")
            ))
        )
        val filter = SelectorsFilter(additionalLabels)
        val hasMatchExpressions = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchExpressions.createMatchExpressions(
            createYAMLSequence(listOf(
                createYAMLSequenceItem("jedi", "In", listOf("yoda", "obiwan", "luke")), // matches jedi/obiwan
                createYAMLSequenceItem("droid", "Exists", emptyList()) // matches droid/r2d2
            ))
        )

        // when
        val isAccepted = filter.isAccepted(hasMatchExpressions)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns true if selector matches all pod labels`() {
        // given
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
        val filter = SelectorsFilter(additionalLabels)
        val hasMatchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchLabels.createMatchLabels(
            createYAMLMapping(listOf(
                createYAMLKeyValue("jedi", "obiwan"),
                createYAMLKeyValue("droid", "r2d2")
            ))
        )
        // when
        val isAccepted = filter.isAccepted(hasMatchLabels)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#isAccepted returns false if selecting pod has no labels`() {
        // given
        val hasNoLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasNoLabels.createLabels(
            createYAMLMapping(emptyList())
        )
        val filter = SelectorsFilter(hasNoLabels)
        val hasMatchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchLabels.createMatchLabels(
            createYAMLMapping(emptyList()) // match all labels
        )
        // when
        val isAccepted = filter.isAccepted(hasMatchLabels)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns false if selecting element is no k8s resource`() {
        // given
        val isNoResource = createYAMLMapping(emptyList()) // missing kind, apiVersion
        val filter = SelectorsFilter(isNoResource)
        val hasMatchLabels = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        hasMatchLabels.createMatchLabels(
            createYAMLMapping(emptyList()) // match all labels
        )
        // when
        val isAccepted = filter.isAccepted(hasMatchLabels)
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
    fun `#isAccepted returns false if filtered deployment has no selector`() {
        // given
        val hasNoSelector = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Deployment")
        ))
        // no selector created
        // when
        val isAccepted = filter.isAccepted(hasNoSelector)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns false if filtering element is a pod but filtered element is no supported type`() {
        // given
        val unsupported = createYAMLMapping(listOf(
            createYAMLKeyValue("kind", "Jedi") // should be Deployment, StatefulSet, CronJob etc.
        ))
        // create selector not to fail on selector check
        unsupported.createSelector(
            createYAMLMapping(emptyList()) // match all
        )
        // when
        val isAccepted = filter.isAccepted(unsupported)
        // then
        assertThat(isAccepted).isFalse()
    }

    @Test
    fun `#isAccepted returns true if filtering is deployment with template labels and filtered is deployment with matching labels`() {
        // given
        deploymentMatchingPod.createTemplate(
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
        val filter = SelectorsFilter(pod)
        // when
        val isAccepted = filter.isAccepted(deploymentMatchingPod)
        // then
        assertThat(isAccepted).isTrue()
    }

    @Test
    fun `#getMatchingElement returns deployment selector that matches given pod labels`() {
        // given
        val filter = SelectorsFilter(deploymentMatchingPod)
        // when
        val matchingElement = filter.getMatchingElement(deploymentMatchingPod)
        // then
        assertThat(matchingElement).isEqualTo(deploymentMatchingPod.getSelector())
    }

    @Test
    fun `#getMatchingElement returns deployment selector that matches given deployment template labels`() {
        // given
        deploymentMatchingPod.createTemplate(
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
        val filter = SelectorsFilter(deploymentMatchingPod)
        // when
        val matchingElement = filter.getMatchingElement(deploymentMatchingPod)
        // then
        assertThat(matchingElement).isEqualTo(deploymentMatchingPod.getSelector())
    }

}
