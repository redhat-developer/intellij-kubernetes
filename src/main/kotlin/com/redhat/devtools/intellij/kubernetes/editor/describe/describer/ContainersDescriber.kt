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

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import com.redhat.devtools.intellij.kubernetes.editor.describe.toRFC1123DateOrUnrecognized
import com.redhat.devtools.intellij.kubernetes.model.util.getStatus
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerPort
import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.EnvFromSource
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.HTTPGetAction
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodStatus
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.VolumeMount

class ContainersDescriber(private val pod: Pod): Describer {

	companion object Labels {
		const val ARGS = "Args"
		const val COMMAND = "Command"
		const val CONFIG_MAP = "ConfigMap"
		const val CONTAINER_ID = "Container ID"
		const val CONTAINERS = "Containers"
		const val ENVIRONMENT = "Environment"
		const val ENVIRONMENT_VARIABLES_FROM = "Environment Variables from"
		const val EXIT_CODE = "Exit Code"
		const val FINISHED = "Finished"
		const val HOST_PORT = "Host Port"
		const val HOST_PORTS = "Host Ports"
		const val IMAGE = "Image"
		const val IMAGE_ID = "Image ID"
		const val INIT_CONTAINERS = "Init Containers"
		const val LAST_STATE = "Last State"
		const val LIMITS = "Limits"
		const val LIVENESS = "Liveness"
		const val MESSAGE = "Message"
		const val MOUNTS = "Mounts"
		const val PORT = "Port"
		const val PORTS = "Ports"
		const val READINESS = "Readiness"
		const val READY = "Ready"
		const val REASON = "Reason"
		const val RESTART_COUNT = "Restart Count"
		const val REQUESTS = "Requests"
		const val RUNNING = "Running"
		const val SECRET = "Secret"
		const val SIGNAL = "Signal"
		const val STARTED = "Started"
		const val STARTUP = "Startup"
		const val STATE = "State"
		const val TERMINATED = "Terminated"
		const val WAITING = "Waiting"
	}

	override fun addTo(chapter: Chapter): Chapter {
		return chapter
			.addChapterIfExists(INIT_CONTAINERS, createContainers(pod.spec?.initContainers, pod.status))
			.addChapter(CONTAINERS, createContainers(pod.spec?.containers, pod.status))
	}

	private fun createContainers(containers: List<Container>?, status: PodStatus?): List<Paragraph> {
		if (containers.isNullOrEmpty()) {
			return emptyList()
		}
		return containers.map { container -> createContainer(container, status) }
	}

	private fun createContainer(container: Container, status: PodStatus?): Paragraph {
		val containerStatus = container.getStatus(status)
		val chapter = Chapter(container.name)
		chapter
			.addIfExists(CONTAINER_ID, containerStatus?.containerID)
			.addIfExists(IMAGE, container.image)
			.addIfExists(IMAGE_ID, containerStatus?.imageID)
			addPorts(container.ports, chapter)
			addHostPorts(container.ports, chapter)
			.addIfExists(COMMAND, com.redhat.devtools.intellij.kubernetes.editor.describe.toString(container.command))
			.addIfExists(ARGS, com.redhat.devtools.intellij.kubernetes.editor.describe.toString(container.args))
			addStatus(STATE, containerStatus?.state, chapter)
			addStatus(LAST_STATE, containerStatus?.lastState, chapter)
			.addIfExists(READY,containerStatus?.ready)
			.addIfExists(RESTART_COUNT, containerStatus?.restartCount)
			addResourceBounds(LIMITS, container.resources?.limits, chapter)
			addResourceBounds(REQUESTS, container.resources?.requests, chapter)
			.addIfExists(LIVENESS, createProbeString(container.livenessProbe))
			.addIfExists(STARTUP, createProbeString(container.startupProbe))
			.addIfExists(READINESS, createProbeString(container.readinessProbe))
			addEnvFrom(container.envFrom, chapter)
			addEnv(container.env, chapter)
			addVolumeMounts(container.volumeMounts, chapter)
		return chapter
	}

	private fun addEnvFrom(envFromSources: List<EnvFromSource>?, parent: Chapter): Chapter {
		if (envFromSources.isNullOrEmpty()) {
			return parent
		}
		return parent
			.addIfExists(
				NamedSequence(ENVIRONMENT_VARIABLES_FROM)
					.addIfExists(
						envFromSources.map { envFrom ->
							createEnvFrom(envFrom)
						}
					)
			)
	}

	private fun createEnvFrom(envFrom: EnvFromSource?): String? {
		val description = when {
			envFrom?.configMapRef != null -> {
				EnvFromDescription(
					CONFIG_MAP,
					envFrom.configMapRef.name,
					envFrom.configMapRef.optional ?: false,
					envFrom.prefix)
			}
			envFrom?.secretRef != null -> {
				EnvFromDescription(
					SECRET,
					envFrom.secretRef.name,
					envFrom.secretRef.optional ?: false,
					envFrom.prefix
				)
			}
			else ->
				null
		}
		return description?.toString()
	}

	private fun addEnv(envVars: MutableList<EnvVar>?, parent: Chapter): Chapter {
		val title = ENVIRONMENT
		val paragraph = if (envVars.isNullOrEmpty()) {
			NamedValue(title, NONE)
		} else {
			Chapter(title).addIfExists(envVars.mapNotNull { envVar ->
				createEnvVar(envVar)
			})
		}
		return parent.addIfExists(paragraph)
	}

	private fun createEnvVar(envVar: EnvVar?): Paragraph? {
		if (envVar == null) {
			return null
		}
		return if (envVar.valueFrom == null) {
			createValueEnvVar(envVar)
		} else {
			createValueFromEnvVar(envVar)
		}
	}

	private fun createValueEnvVar(envVar: EnvVar): Paragraph {
		val value = when {
			!envVar.value.isNullOrBlank() -> {
				if (envVar.value.contains("\n")) {
					"|\n${envVar.value}"
				} else {
					envVar.value
				}
			}
			else ->
				NONE
		}
		return NamedValue(envVar.name, value)
	}

	private fun createValueFromEnvVar(envVar: EnvVar): Paragraph? {
		val value = when {
			envVar.valueFrom.fieldRef != null -> {
				// TODO: implement k8s.io/kubectl/pkg/describe/describe.go/resolverFn()
				// see https://github.com/redhat-developer/intellij-kubernetes/issues/774
				val fieldRef = envVar.valueFrom.fieldRef
				"(${fieldRef.apiVersion}:${fieldRef.fieldPath})"
			}
			envVar.valueFrom.resourceFieldRef != null -> {
				// TODO: implement k8s.io/kubectl/pkg/util/resource/resourcehelper.ExtractContainerResourceValue(e.ValueFrom.ResourceFieldRef, &container)
				// see https://github.com/redhat-developer/intellij-kubernetes/issues/774
				val resourceFieldRef = envVar.valueFrom.resourceFieldRef
				"(${resourceFieldRef.containerName}:${resourceFieldRef.resource})"
			}
			envVar.valueFrom.secretKeyRef != null -> {
				val secretKeyRef = envVar.valueFrom.secretKeyRef
				"<set to the key ${secretKeyRef.key} in secret ${secretKeyRef.name}> Optional: ${secretKeyRef.optional ?: false}"
			}
			envVar.valueFrom.configMapKeyRef != null -> {
				val configMapKeyRef = envVar.valueFrom.configMapKeyRef
				"<set to the key ${configMapKeyRef.key} of config map ${configMapKeyRef.name}> Optional: ${configMapKeyRef.optional ?: false}"
			}
			else ->
				null
		}
		return NamedValue(envVar.name, value)
	}

	private fun addVolumeMounts(volumeMounts: List<VolumeMount>, parent: Chapter) {
		val title = MOUNTS
		if (volumeMounts.isEmpty()) {
			parent.addIfExists(title, NONE)
			return
		}
		parent.addIfExists(
			NamedSequence(title, volumeMounts.map { volumeMount ->
				createVolumeMount(volumeMount)
			})
		)
	}

	private fun createVolumeMount(volumeMount: VolumeMount): String {
		val flags = if (volumeMount.readOnly == true) {
			"ro"
		} else {
			"rw"
		}
		val subPath = if (volumeMount.subPath.isNullOrEmpty()) {
			""
		} else {
			", path = \"${volumeMount.subPath}\""
		}
		return String.format("%s from %s (%s)", volumeMount.mountPath, volumeMount.name, "$flags$subPath")
	}

	private fun addPorts(ports: List<ContainerPort>, chapter: Chapter): Chapter {
		val title = if (ports.size > 1) PORTS else PORT
		val value = toString({ port -> port.containerPort }, ports)
		return chapter.add(title, value)
	}

	private fun addHostPorts(ports: List<ContainerPort>, chapter: Chapter): Chapter {
		val title = if (ports.size > 1) HOST_PORTS else HOST_PORT
		val value = toString({ port -> port.hostPort }, ports)
		return chapter.add(title, value)
	}

	private fun toString(portProvider: (ContainerPort) -> Int?, ports: List<ContainerPort>?): String? {
		if (ports.isNullOrEmpty()) {
			return null
		}
		return ports.joinToString(", ") { port ->
			"${portProvider.invoke(port) ?: 0}/${port.protocol}"
		}
	}

	private fun addStatus(label: String, state: ContainerState?, chapter: Chapter): Chapter {
		return when {
			state == null ->
				return chapter
			state.running != null -> {
				chapter.addIfExists(
					Chapter(label)
						.addIfExists(STATE, RUNNING)
						.addIfExists(STARTED, toRFC1123DateOrUnrecognized(state.running.startedAt))
				)
			}
			state.waiting != null -> {
				chapter.addIfExists(
					Chapter(label)
						.addIfExists(STATE, WAITING)
						.addIfExists(REASON, state.waiting.reason)
				)
			}
			state.terminated != null -> {
				chapter.addIfExists(
					Chapter(label)
						.addIfExists(STATE, TERMINATED)
						.addIfExists(REASON, state.terminated.reason)
						.addIfExists(MESSAGE, state.terminated.message)
						.addIfExists(EXIT_CODE, state.terminated.exitCode)
						.addIfExists(SIGNAL, state.terminated.signal)
						.addIfExists(STARTED, toRFC1123DateOrUnrecognized(state.terminated.startedAt))
						.addIfExists(FINISHED, toRFC1123DateOrUnrecognized(state.terminated.finishedAt))
				)
			}
			else ->
				chapter.addIfExists(label, WAITING)
		}
	}

	private fun addResourceBounds(label: String, bounds: Map<String, io.fabric8.kubernetes.api.model.Quantity>?, parent: Chapter): Chapter {
		if (bounds.isNullOrEmpty()) {
			return parent
		}
		val limits = Chapter(label)
		bounds.toSortedMap().forEach { limit ->
			limits.addIfExists(limit.key, limit.value.toString())
		}
		return parent.addIfExists(limits)
	}

	private fun createProbeString(probe: Probe?): String? {
		if (probe == null) {
			return null
		}
		val attributes = String.format("delay=%ds timeout=%ds period=%ds #success=%d #failure=%d",
			probe.initialDelaySeconds ?: 0,
			probe.timeoutSeconds ?: 0,
			probe.periodSeconds ?: 0,
			probe.successThreshold ?: 0,
			probe.failureThreshold ?: 0)
		return when {
			probe.exec != null -> {
				val command = probe.exec?.command?.joinToString (" ") ?: NONE
				String.format("exec [%s] %s", command, attributes)
			}
			probe.httpGet != null -> {
				String.format("http-get %s %s", toUrl(probe.httpGet), attributes)
			}
			probe.tcpSocket != null -> {
				val host = probe.tcpSocket.host ?: "" // null is formatted to 'null'
				val port = probe.tcpSocket.port?.value?.toString()
				if (port.isNullOrBlank()) {
					String.format("tcp-socket %s %s", host, attributes)
				} else {
					String.format("tcp-socket %s:%s %s", host, port, attributes)
				}
			}
			probe.grpc != null -> {
				String.format("grpc <pod>:%d %s %s", probe.grpc.port ?: NONE, probe.grpc.service, attributes)
			}
			else ->
				String.format("unknown %s", attributes)
		}
	}

	private fun toUrl(httpGet: HTTPGetAction): String {
		val scheme = httpGet.scheme?.lowercase() ?: ""
		val host = httpGet.host ?: ""
		val port = httpGet.port?.value?.toString() ?: ""
		val path = if (httpGet.path == null) {
			""
		} else if (!httpGet.path.startsWith("/")) {
			"/${httpGet.path}"
		} else {
			httpGet.path
		}
		return "$scheme://$host:$port$path"
	}

	private data class EnvFromDescription(
		private val type: String,
		private val name: String,
		private val optional: Boolean,
		private val prefix: String? = null
	) {

		private fun hasPrefix(): Boolean {
			return !prefix.isNullOrBlank()
		}

		override fun toString(): String {
			return if (hasPrefix()) {
				String.format("%s %s with prefix \"%s\" Optional: %s",
					name,
					type,
					prefix,
					optional)
			} else {
				String.format("%s %s Optional: %s",
					name,
					type,
					optional)
			}
		}
	}
}