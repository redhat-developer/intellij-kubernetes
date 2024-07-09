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


import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Labels.NAME
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Labels.NAMESPACE
import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.createValues
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.toHumanReadableDurationSince
import com.redhat.devtools.intellij.kubernetes.editor.describe.toRFC1123DateOrUnrecognized
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.isSeccompProfileLocalhost
import com.redhat.devtools.intellij.kubernetes.model.util.PodUtils.isTerminating
import com.redhat.devtools.intellij.kubernetes.model.util.TOLERATION_OPERATOR_EXISTS
import com.redhat.devtools.intellij.kubernetes.model.util.toBooleanOrNull
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodCondition
import io.fabric8.kubernetes.api.model.PodIP
import io.fabric8.kubernetes.api.model.PodReadinessGate
import io.fabric8.kubernetes.api.model.PodStatus
import io.fabric8.kubernetes.api.model.SeccompProfile
import io.fabric8.kubernetes.api.model.Toleration
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil
import java.time.LocalDateTime

class PodDescriber(private val pod: Pod): Describer {

	companion object Labels {
		const val NO_HOST_IP = "<No Host IP>"

		const val ANNOTATIONS = "Annotations"
		const val CONDITIONS = "Conditions"
		const val CONTROLLED_BY = "Controlled By"
		const val IP = "IP"
		const val IPS = "IPs"
		const val LABELS = "Labels"
		const val LOCALHOST_PROFILE = "LocalhostProfile"
		const val MESSAGE = "Message"
		const val NODE = "Node"
		const val NODE_SELECTORS = "Node-Selectors"
		const val NOMINATED_NODE_NAME = "NominatedNodeName"
		const val PRIORITY = "Priority"
		const val PRIORITY_CLASS_NAME = "Priority Class Name"
		const val QOS_CLASS = "QoS Class"
		const val READINESS_GATES = "Readiness Gates"
		const val REASON = "Reason"
		const val RUNTIME_CLASS_NAME = "Runtime Class Name"
		const val SECCOMP_PROFILE = "SeccompProfile"
		const val SERVICE_ACCOUNT = "Service Account"
		const val START_TIME = "Start Time"
		const val STATUS = "Status"
		const val TERMINATION_GRACE_PERIOD = "Termination Grace Period"
		const val TOLERATIONS = "Tolerations"
	}

	override fun addTo(chapter: Chapter): Chapter {
		chapter
			.addIfExists(NAME, pod.metadata?.name)
			.addIfExists(NAMESPACE, pod.metadata?.namespace)
			.addIfExists(PRIORITY, pod.spec?.priority)
			.addIfExists(PRIORITY_CLASS_NAME, pod.spec?.priorityClassName)
			.addIfExists(RUNTIME_CLASS_NAME, pod.spec?.runtimeClassName)
			.addIfExists(SERVICE_ACCOUNT, pod.spec?.serviceAccountName)
			.add(NODE, createNode(pod))
			.addIfExists(START_TIME, toRFC1123DateOrUnrecognized(pod.status?.startTime))
			.addChapter(LABELS, createValues(pod.metadata?.labels))
			.addChapter(ANNOTATIONS, createValues(pod.metadata?.annotations))
			addStatus(pod, chapter)
			.addIfExists(REASON, pod.status?.reason)
			.addIfExists(MESSAGE, pod.status?.message)
			addPodSeccompProfile(pod.spec?.securityContext?.seccompProfile, chapter)
			// deprecated, to be removed once not available anymore
			chapter.add(IP, pod.status?.podIP)
			addIPs(pod.status?.podIPs, chapter)
			.addIfExists(CONTROLLED_BY, createControlledBy(pod))
			.addIfExists(NOMINATED_NODE_NAME, pod.status?.nominatedNodeName)
			ContainersDescriber(pod).addTo(chapter)
			addReadinessGates(pod, chapter)
			addConditions(pod.status, chapter)
			if (pod.spec != null) {
				VolumesDescriber(pod.spec.volumes).addTo(chapter)
			}
			chapter.add(QOS_CLASS, pod.status?.qosClass)
			.addChapter(NODE_SELECTORS, createValues(pod.spec?.nodeSelector))
			.addSequence(TOLERATIONS, createTolerations(pod.spec?.tolerations))
		return chapter
	}

	private fun addStatus(pod: Pod, chapter: Chapter): Chapter {
		if (pod.isTerminating()) {
			chapter
				.addIfExists(
					STATUS, "Terminating: (lasts ${
						toHumanReadableDurationSince(pod.metadata?.deletionTimestamp, LocalDateTime.now())
					}")
				.addIfExists(TERMINATION_GRACE_PERIOD, "${pod.metadata.deletionGracePeriodSeconds}s")
		} else {
			chapter.addIfExists(STATUS, pod.status?.phase)
		}
		return chapter
	}

	private fun addPodSeccompProfile(seccompProfile: SeccompProfile?, chapter: Chapter): Chapter {
		chapter.addIfExists(SECCOMP_PROFILE, seccompProfile?.type)
		if (seccompProfile?.isSeccompProfileLocalhost() == true) {
			chapter.addIfExists(LOCALHOST_PROFILE, seccompProfile.localhostProfile)
		}
		return chapter
	}

	private fun createNode(pod: Pod): String? {
		return if (pod.spec?.nodeName.isNullOrBlank()) {
			null
		} else {
			"${pod.spec.nodeName}/${ pod.status?.hostIP ?: NO_HOST_IP}"
		}
	}

	private fun addIPs(ips: List<PodIP>?, parent: Chapter): Chapter {
		if (ips.isNullOrEmpty()) {
			parent.addIfExists(IPS, NONE)
		} else {
			parent.addChapter(IPS, ips.mapNotNull { podIp ->
				if (podIp.ip.isNullOrBlank()) {
					null
				} else {
					NamedValue(IP, podIp.ip)
				}
			})
		}
		return parent
	}

	private fun createControlledBy(pod: Pod): String? {
		val controller = KubernetesResourceUtil.getControllerUid(pod)
		return if (controller != null) {
			"${controller.kind}/${controller.name}"
		} else {
			null
		}
	}

	private fun addReadinessGates(pod: Pod, parent: Chapter): Chapter {
		val readinessGates = pod.spec?.readinessGates
		if (readinessGates.isNullOrEmpty()) {
			return parent
		}
		val readinessChapter = Chapter(READINESS_GATES)
			.addIfExists(readinessGates.mapNotNull { readinessGate ->
				createReadinessGate(readinessGate, pod.status?.conditions)
			}
		)
		parent.addIfExists(readinessChapter)
		return parent
	}

	private fun createReadinessGate(readinessGate: PodReadinessGate, conditions: List<PodCondition>?): Paragraph {
		val condition = getCondition(readinessGate.conditionType, conditions)
		return NamedValue(
			readinessGate.conditionType,
			condition?.status?.toBooleanOrNull() ?: NONE
		)
	}

	private fun getCondition(type: String?, conditions: List<PodCondition>?): PodCondition? {
		if (type.isNullOrEmpty()
			|| conditions.isNullOrEmpty()) {
			return null
		}
		return conditions.find { condition -> type == condition.type }
	}

	private fun addConditions(status: PodStatus?, parent: Chapter): Chapter {
		val conditions = status?.conditions
		if (conditions.isNullOrEmpty()) {
			return parent
		}
		val conditionsChapter = Chapter(CONDITIONS)
			.addIfExists(conditions.mapNotNull { condition ->
				NamedValue(
					condition.type,
					condition.status?.toBooleanOrNull() ?: NONE
				)
			})
		return parent.addIfExists(conditionsChapter)
	}

	private fun createTolerations(tolerations: List<Toleration>?): List<String>? {
		return tolerations?.map { toleration ->
			toString(toleration)
		}
	}

	private fun toString(toleration: Toleration): String {
		val builder = StringBuilder(toleration.key)
		if (!toleration.value.isNullOrEmpty()) {
			builder.append("=${toleration.value}")
		}
		if (!toleration.effect.isNullOrBlank()) {
			builder.append(":${toleration.effect}")
		}
		if (toleration.operator == TOLERATION_OPERATOR_EXISTS
			&& toleration.value.isNullOrBlank()) {
			if (!toleration.key.isNullOrEmpty()
				|| !toleration.effect.isNullOrEmpty()) {
				builder.append(" ")
			}
			builder.append("op=Exists")
		}
		if (toleration.tolerationSeconds != null) {
			builder.append(" for ${toleration.tolerationSeconds}s")
		}
		return builder.toString()
	}
}