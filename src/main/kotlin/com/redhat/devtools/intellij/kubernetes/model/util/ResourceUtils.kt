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
package com.redhat.devtools.intellij.kubernetes.model.util

import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.KubernetesVersionPriority
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version
import io.fabric8.kubernetes.model.util.Helper
import java.util.stream.Collectors

const val MARKER_WILL_BE_DELETED = "willBeDeleted"

/**
 * returns {@code true} if the given resource has the same
 * <ul>
 *     <li>kind</li>
 *     <li>apiVersion</li>
 *     <li>name</li>
 *     <li>namespace</li>
 * </ul>
 * This allows to identify resource equality in different instances and when enriched by the server
 *
 * @see io.fabric8.kubernetes.api.model.HasMetadata.getKind
 * @see io.fabric8.kubernetes.api.model.HasMetadata.getApiVersion
 * @see io.fabric8.kubernetes.api.model.ObjectMeta.name
 * @see io.fabric8.kubernetes.api.model.ObjectMeta.name
 */
fun HasMetadata.isSameResource(resource: HasMetadata): Boolean {
	return isSameKind(resource)
			&& isSameApiVersion(resource)
			&& isSameName(resource)
			&& isSameNamespace(resource)
}

fun HasMetadata.isSameApiVersion(resource: HasMetadata): Boolean {
	if (this.apiVersion == null
		&& resource.apiVersion == null) {
		return false
	}
	return apiVersion == resource.apiVersion
}

/**
 * returns {@code true} if the given resource has the same uid as this resource. Returns {@code false} otherwise.
 *
 * @see io.fabric8.kubernetes.api.model.ObjectMeta.getUid()
 */
fun HasMetadata.isSameUid(resource: HasMetadata): Boolean {
	if (this.metadata?.uid == null
		&& resource.metadata?.uid == null) {
		return false
	}
	return resource.metadata?.uid == this.metadata?.uid
}

fun HasMetadata.isSameName(resource: HasMetadata): Boolean {
	if (this.metadata?.name == null
		&& resource.metadata?.name == null) {
		return true
	}
	return this.metadata?.name == resource.metadata?.name
}

fun HasMetadata.isSameNamespace(resource: HasMetadata): Boolean {
	if (this.metadata?.namespace == null
		&& resource.metadata?.namespace == null) {
		return true
	}
	return this.metadata?.namespace == resource.metadata?.namespace
}

fun HasMetadata.isSameKind(resource: HasMetadata): Boolean {
	if (this.kind == null
		&& resource.kind == null) {
		return true
	}
	return this.kind == resource.kind
}

fun HasMetadata.isSameRevision(resource: HasMetadata): Boolean {
	if (this.metadata?.resourceVersion == null
		&& resource.metadata?.resourceVersion == null) {
		return true
	}
	return this.metadata?.resourceVersion == resource.metadata?.resourceVersion
}

fun HasMetadata.isNewerVersionThan(resource: HasMetadata): Boolean {
	if (!this.isSameResource(resource)) {
		return false
	}
	val thisVersion = this.metadata?.resourceVersion?.toIntOrNull()
	val thatVersion = resource.metadata?.resourceVersion?.toIntOrNull()
	if (thisVersion == null) {
		return thatVersion == null
	}
	if (thatVersion == null) {
		return true
	}
	return  thisVersion > thatVersion
}

/**
 * Returns the version for a given subclass of HasMetadata.
 * The version is built of the apiGroup and apiVersion that are annotated in the HasMetadata subclasses.
 *
 * @see HasMetadata.getApiVersion
 * @see io.fabric8.kubernetes.model.annotation.Version (annotation)
 * @see io.fabric8.kubernetes.model.annotation.Group (annotation)
 */
fun getApiVersion(clazz: Class<out HasMetadata>): String {
	val apiVersion = Helper.getAnnotationValue(clazz, Version::class.java)
	return if (!apiVersion.isNullOrBlank()) {
		val apiGroup = Helper.getAnnotationValue(clazz, Group::class.java)
		if (!apiGroup.isNullOrBlank()) {
			getApiVersion(apiGroup, apiVersion)
		} else {
			apiVersion
		}
	} else {
		clazz.simpleName
	}
}

/**
 * Returns the version for given apiGroup and apiVersion. Both values are concatenated and separated by '/'.
 */
fun getApiVersion(apiGroup: String, apiVersion: String) = "$apiGroup/$apiVersion"

fun getVersion(spec: CustomResourceDefinitionSpec): String {
	val versions = spec.versions.map { it.name }
	return KubernetesVersionPriority.highestPriority(versions) ?: spec.version
}

fun createContext(definition: CustomResourceDefinition): CustomResourceDefinitionContext {
	return CustomResourceDefinitionContext.Builder()
		.withGroup(definition.spec.group)
		.withVersion(getVersion(definition.spec)) // use version with highest priority
		.withScope(definition.spec.scope)
		.withName(definition.metadata.name)
		.withPlural(definition.spec.names.plural)
		.withKind(definition.spec.names.kind)
		.build()
}

fun setDeletionTimestamp(timestamp: String, resource: HasMetadata) {
	resource.metadata.deletionTimestamp = timestamp
}

fun hasDeletionTimestamp(resource: HasMetadata?): Boolean {
	return null != resource?.metadata?.deletionTimestamp
}

fun setWillBeDeleted(resource: HasMetadata) {
	setDeletionTimestamp(MARKER_WILL_BE_DELETED, resource)
}

fun isWillBeDeleted(resource: HasMetadata?): Boolean {
	return MARKER_WILL_BE_DELETED == resource?.metadata?.deletionTimestamp
}

/**
 * Returns a message listing the given resources by name while using ',' as delimiter.
 * Duplicate resources are ignored. Names that are longer than 20 characters are trimmed.
 *
 * @see toMessage
 * @see trim
 */
fun toMessage(resources: Collection<HasMetadata>, maxLength: Int): String {
	return resources.stream()
		.distinct()
		.map { toMessage(it, maxLength) }
		.collect(Collectors.joining(",\n"))
}

fun toMessage(resource: HasMetadata, maxLength: Int): String {
	return "${resource.kind} \"${trimWithEllipsis(resource.metadata.name, maxLength)}\""
}

/**
 * Trims a string to the given length. A negative length is interpreted as no trimming to be applied.
 * The strategy that's applied is trying to preserve the trailing 3 chars which are especially important
 * in resource names (starting chars usually the same, trailing portion differing).
 * The following rules are applied:
 * <ul>
 *		<li>if the string is shorter than the requested length, the message is returned as is</li>
 *		<li>if the string is longer than the requested length of <= 4, the requested length with ellipsis is used and
 *		then trimmed to the requested length</li>
 *		<li>if the string is longer than the requested length of <= 6, then the given message is trimmed
 *		and ellipsis appended</li>
 *		<li>if the string is longer than the requested length, then the given message is trimmed, ellipsis and
 *		the last 3 characters are appended</li>
 * </ul>
 */
fun trimWithEllipsis(value: String?, length: Int): String? {
	if (value == null) {
		return null
	}
	return when {
		length < 0
				|| length >= value.length
			-> value
		length <= 4
			-> ("${value}...").take(length)
		length <= 6
			-> "${value.take(length - 3)}..."
		else -> "${value.take(length - 6)}...${value.takeLast(3)}"
	}
}

/**
 * Returns an instance of the given type for the given json or yaml string.
 *
 * @param jsonYaml the string that should be unmarshalled
 * @return the instance of the given type
 */
inline fun <reified T> createResource(jsonYaml: String): T {
	return Serialization.unmarshal(jsonYaml, T::class.java)
}

