/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.describe.describer

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getChildren
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.getParagraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriberTestUtils.toMap
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Labels.NAME
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Labels.NAMESPACE
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.NO_HOST_IP
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.ANNOTATIONS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.CONDITIONS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.CONTROLLED_BY
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.IP
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.IPS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.PRIORITY
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.PRIORITY_CLASS_NAME
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.RUNTIME_CLASS_NAME
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.NODE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.LABELS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.LOCALHOST_PROFILE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.MESSAGE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.NODE_SELECTORS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.NOMINATED_NODE_NAME
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.QOS_CLASS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.READINESS_GATES
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.REASON
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.SECCOMP_PROFILE
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.SERVICE_ACCOUNT
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.START_TIME
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.STATUS
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.TERMINATION_GRACE_PERIOD
import com.redhat.devtools.intellij.kubernetes.editor.describe.describer.PodDescriber.Labels.TOLERATIONS
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.toRFC1123DateOrUnrecognized
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils
import com.redhat.devtools.intellij.kubernetes.model.util.TOLERATION_OPERATOR_EQUAL
import com.redhat.devtools.intellij.kubernetes.model.util.TOLERATION_OPERATOR_EXISTS
import io.fabric8.kubernetes.api.model.OwnerReference
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.PodCondition
import io.fabric8.kubernetes.api.model.PodIP
import io.fabric8.kubernetes.api.model.PodReadinessGate
import io.fabric8.kubernetes.api.model.Toleration
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PodDescriberTest {

	@Test
	fun `should describe name`() {
		// given
		val pod = PodBuilder()
			.withNewMetadata()
				.withName("luke")
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(NAME, description)
		assertThat(paragraph?.value).isEqualTo("luke")
	}

	@Test
	fun `should describe namespace`() {
		// given
		val pod = PodBuilder()
			.withNewMetadata()
				.withNamespace("rebellion")
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(NAMESPACE, description)
		assertThat(paragraph?.value).isEqualTo("rebellion")
	}

	@Test
	fun `should describe priority`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withPriority(42)
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(PRIORITY, description)
		assertThat(paragraph?.value).isEqualTo(42)
	}

	@Test
	fun `should describe priority class name`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withPriorityClassName("jedis")
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(PRIORITY_CLASS_NAME, description)
		assertThat(paragraph?.value).isEqualTo("jedis")
	}

	@Test
	fun `should not describe priority class name if pod has no priority class name`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(PRIORITY_CLASS_NAME, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe runtime class name`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withRuntimeClassName("x-wing")
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(RUNTIME_CLASS_NAME, description)
		assertThat(paragraph?.value).isEqualTo("x-wing")
	}

	@Test
	fun `should NOT describe runtime class name if pod has no runtime class name`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(RUNTIME_CLASS_NAME, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe service account name`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withServiceAccountName("obiwan")
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(SERVICE_ACCOUNT, description)
		assertThat(paragraph?.value).isEqualTo("obiwan")
	}

	@Test
	fun `should not describe service account name if pod has no service account name`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(SERVICE_ACCOUNT, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe node as NONE if pod has no node`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val nodeValue = getParagraph<NamedValue>(NODE, description)
		assertThat(nodeValue?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe node as name & hostIp if pod has node`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withNodeName("leia")
			.endSpec()
			.withNewStatus()
				.withHostIP("jedi")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val nodeValue = getParagraph<NamedValue>(NODE, description)
		assertThat(nodeValue?.value).isEqualTo("leia/jedi")
	}

	@Test
	fun `should describe node as name & noIp if pod has no nodeIp`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withNodeName("leia")
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val nodeValue = getParagraph<NamedValue>(NODE, description)
		assertThat(nodeValue?.value).isEqualTo("leia/${NO_HOST_IP}")
	}

	@Test
	fun `should describe start time`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withStartTime("2024-07-15T14:59:19Z")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(START_TIME, description)
		assertThat(paragraph?.value).isEqualTo(toRFC1123DateOrUnrecognized("2024-07-15T14:59:19Z"))
	}

	@Test
	fun `should NOT describe start time if pod has no start time`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(START_TIME, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe labels`() {
		// given
		val pod = PodBuilder()
			.withNewMetadata()
				.withLabels<String, String>(
					mapOf(
						"luke" to "skywalker",
						"princess" to "leia"
					)
				)
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(LABELS, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"luke" to "skywalker",
				"princess" to "leia"
			)
		)
	}

	@Test
	fun `should describe labels with NONE if there are no labels`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(LABELS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe annotations`() {
		// given
		val pod = PodBuilder()
			.withNewMetadata()
			.withAnnotations<String, String>(
				mapOf(
					"luke" to "skywalker",
					"princess" to "leia"
				)
			)
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(ANNOTATIONS, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"luke" to "skywalker",
				"princess" to "leia"
			)
		)
	}

	@Test
	fun `should describe annotations with NONE if there are no annotations`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(ANNOTATIONS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe status if pod is NOT terminating`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withPhase("Looking for the force")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(STATUS, description)
		assertThat(paragraph?.value).isEqualTo("Looking for the force")
	}

	@Test
	fun `should describe status and grace period if pod is terminating`() {
		// given
		val deletion = "2024-07-15T14:59:19Z"
		val pod = PodBuilder()
			.withNewMetadata()
				.withDeletionTimestamp(deletion)
				.withDeletionGracePeriodSeconds(42)
			.endMetadata()
			.withNewStatus()
				.withPhase("Looking for the force")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val status = getParagraph<NamedValue>(STATUS, description)
		assertThat(status?.value.toString()).startsWith("Terminating: (lasts")
		val gracePeriod = getParagraph<NamedValue>(TERMINATION_GRACE_PERIOD, description)
		assertThat(gracePeriod?.value).isEqualTo("42s")
	}

	@Test
	fun `should describe reason`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withReason("luke lost the light saber")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(REASON, description)
		assertThat(paragraph?.value).isEqualTo("luke lost the light saber")
	}

	@Test
	fun `should NOT describe reason if pod has no reason`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(REASON, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should NOT describe message if pod has no message`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(MESSAGE, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe message`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withMessage("leia broke the light saber")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(MESSAGE, description)
		assertThat(paragraph?.value).isEqualTo("leia broke the light saber")
	}

	@Test
	fun `should NOT describe seccomp profile if pod has no seccomp profile`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(SECCOMP_PROFILE, description)
		assertThat(paragraph).isNull()
	}

	@Test
	fun `should describe seccomp profile`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withNewSecurityContext()
					.withNewSeccompProfile()
						.withType("Ice Planet Hoth")
					.endSeccompProfile()
				.endSecurityContext()
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(SECCOMP_PROFILE, description)
		assertThat(paragraph?.value).isEqualTo("Ice Planet Hoth")
		val localhost = getParagraph<NamedValue>(LOCALHOST_PROFILE, description)
		assertThat(localhost).isNull()
	}

	@Test
	fun `should describe seccomp profile and localhost profile if seccomp profile is localhost`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withNewSecurityContext()
					.withNewSeccompProfile()
						.withType(PodUtils.SECCOMP_PROFILE_LOCALHOST)
						.withLocalhostProfile("Alderaan")
					.endSeccompProfile()
				.endSecurityContext()
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val seccompProfile = getParagraph<NamedValue>(SECCOMP_PROFILE, description)
		assertThat(seccompProfile?.value).isEqualTo(PodUtils.SECCOMP_PROFILE_LOCALHOST)
		val localhost = getParagraph<NamedValue>(LOCALHOST_PROFILE, description)
		assertThat(localhost?.value).isEqualTo("Alderaan")
	}

	@Test
	fun `should describe IP`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withPodIP("42")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(IP, description)
		assertThat(paragraph?.value).isEqualTo("42")
	}

	@Test
	fun `should describe IP with NONE if pod has no IP`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(IP, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe IPs with NONE if pod has no IPs`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(IPS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe IPs`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withPodIPs(
					PodIP("42"),
					PodIP("84"),
					PodIP("168")
				)
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(IPS, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"IP" to "42",
				"IP" to "84",
				"IP" to "168"
			)
		)
	}

	@Test
	fun `should NOT describe controller if pod has no owner reference`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(CONTROLLED_BY, description)
		assertThat(paragraph?.value).isNull()
	}

	@Test
	fun `should NOT describe controller if there's no controller owner reference`() {
		// given
		val leia: OwnerReference = OwnerReference().apply {
			kind = "princess"
			name = "leia"
			controller = false
		}

		val yoda: OwnerReference = OwnerReference().apply {
			kind = "jedi"
			name = "yoda"
			controller = false
		}

		val pod = PodBuilder()
			.withNewMetadata()
				.withOwnerReferences(leia, yoda)
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(CONTROLLED_BY, description)
		assertThat(paragraph?.value).isNull()
	}

	@Test
	fun `should describe controller`() {
		// given
		val leia: OwnerReference = OwnerReference().apply {
			kind = "princess"
			name = "leia"
			controller = true
		}
		val yoda: OwnerReference = OwnerReference().apply {
			kind = "jedi"
			name = "yoda"
			controller = false
		}

		val pod = PodBuilder()
			.withNewMetadata()
				.withOwnerReferences(yoda, leia)
			.endMetadata()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(CONTROLLED_BY, description)
		assertThat(paragraph?.value).isEqualTo("princess/leia")
	}

	@Test
	fun `should NOT describe nominated node name if pod has no nominated node name`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(NOMINATED_NODE_NAME, description)
		assertThat(paragraph?.value).isNull()
	}

	@Test
	fun `should describe nominated node name`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
				.withNominatedNodeName("yoda")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(NOMINATED_NODE_NAME, description)
		assertThat(paragraph?.value).isEqualTo("yoda")
	}

	@Test
	fun `should NOT describe readiness gates if pod has no readiness gates`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(READINESS_GATES, description)
		assertThat(paragraph?.value).isNull()
	}

	@Test
	fun `should describe readiness gates`() {
		// given
		val condition1 = PodCondition().apply {
			type = "rebellion saves the republic"
			status = true.toString()
		}
		val condition2 = PodCondition().apply {
			type = "emperor destroys the republic"
			// no status -> <None>
		}
		val pod = PodBuilder()
			.withNewSpec()
				.withReadinessGates(
					PodReadinessGate(condition1.type),
					PodReadinessGate(condition2.type)
				)
			.endSpec()
			.withNewStatus()
				.withConditions(
					condition1,
					condition2
				)
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(READINESS_GATES, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"rebellion saves the republic" to true,
				"emperor destroys the republic" to NONE
			)
		)
	}

	@Test
	fun `should describe readiness gates with NONE value if condition doesn't exist`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
				.withReadinessGates(
					PodReadinessGate("yoda"),
				)
			.endSpec()
			// no conditions
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(READINESS_GATES, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"yoda" to NONE
			)
		)
	}

	@Test
	fun `should NOT describe conditions if pod has no conditions`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(CONDITIONS, description)
		assertThat(paragraph?.value).isNull()
	}

	@Test
	fun `should describe conditions`() {
		// given
		val condition1 = PodCondition().apply {
			type = "rebellion saves the republic"
			status = true.toString()
		}
		val condition2 = PodCondition().apply {
			type = "emperor destroys the republic"
			status = "non-boolean" // false
		}
		val pod = PodBuilder()
			.withNewStatus()
				.withConditions(
					condition1,
					condition2
				)
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(CONDITIONS, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"rebellion saves the republic" to true,
				"emperor destroys the republic" to false
			)
		)
	}

	@Test
	fun `should describe QoS class`() {
		// given
		val pod = PodBuilder()
			.withNewStatus()
			.withQosClass("brave rebellion")
			.endStatus()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(QOS_CLASS, description)
		assertThat(paragraph?.value).isEqualTo("brave rebellion")
	}

	@Test
	fun `should describe QoS class with NONE if pod has no QoS class`() {
		// given
		val pod = PodBuilder()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(QOS_CLASS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe node selectors`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withNodeSelector<String, String>(
				mapOf(
					"luke" to "death star",
					"princess" to "alderaan"
				)
			)
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraphs = toMap(getChildren<Chapter, NamedValue>(NODE_SELECTORS, description))
		assertThat(paragraphs).containsExactlyEntriesOf(
			mapOf(
				"luke" to "death star",
				"princess" to "alderaan"
			)
		)
	}

	@Test
	fun `should describe node selectors with NONE if there are no node selectors`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(NODE_SELECTORS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}

	@Test
	fun `should describe tolerations`() {
		// given
		val pod = PodBuilder()
			.withNewSpec()
			.withTolerations(
				Toleration(
					"total-anger-control",
					"yoda",
					TOLERATION_OPERATOR_EXISTS,
					42,
					null
				),
				Toleration(
					"hot-headed",
					"anakin",
					TOLERATION_OPERATOR_EQUAL,
					null,
					"eaten by anger"
				)
			)
			.endSpec()
			.build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph: NamedSequence? = getParagraph(TOLERATIONS, description)
		assertThat(paragraph?.children).containsExactly(
			"yoda:total-anger-control op=Exists for 42s",
			"anakin=eaten by anger:hot-headed"
		)
	}

	@Test
	fun `should describe tolerations with NONE if there are no tolerations`() {
		// given
		val pod = PodBuilder().build()
		// when
		val description = PodDescriber(pod).addTo(Chapter(""))
		// then
		val paragraph = getParagraph<NamedValue>(TOLERATIONS, description)
		assertThat(paragraph?.value).isEqualTo(NONE)
	}
}