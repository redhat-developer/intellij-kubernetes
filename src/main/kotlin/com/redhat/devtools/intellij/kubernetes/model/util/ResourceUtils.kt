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

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.KubernetesVersionPriority
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version
import io.fabric8.kubernetes.model.util.Helper
import java.util.stream.Collectors

const val MARKER_WILL_BE_DELETED = "willBeDeleted"
const val API_GROUP_VERSION_DELIMITER = '/'

/**
 * returns `true` if the given resource has the same
 * - kind
 * - apiVersion
 * - name
 * - namespace
 * regardless of other differences.
 * This method allows to determine resource identity across instances regardless of updates (ex. resource version) applied by the server
 *
 * @see io.fabric8.kubernetes.api.model.HasMetadata.getKind
 * @see io.fabric8.kubernetes.api.model.HasMetadata.getApiVersion
 * @see io.fabric8.kubernetes.api.model.ObjectMeta.name
 * @see io.fabric8.kubernetes.api.model.ObjectMeta.name
 */
fun HasMetadata.isSameResource(resource: HasMetadata?): Boolean {
	if (resource == null) {
		return false
	}
	return isSameKind(resource)
			&& isSameApiVersion(resource)
			&& isSameName(resource)
			&& isSameNamespace(resource)
}

fun HasMetadata.isSameApiVersion(resource: HasMetadata?): Boolean {
	if (resource == null) {
		return false
	} else if (this.apiVersion == null
		&& resource.apiVersion == null) {
		return false
	}
	return apiVersion == resource.apiVersion
}

fun HasMetadata.isSameName(resource: HasMetadata?): Boolean {
	if (resource == null) {
		return false
	} else if (this.metadata?.name == null
		&& resource.metadata?.name == null) {
		return true
	}
	return this.metadata?.name == resource.metadata?.name
}

fun HasMetadata.isSameNamespace(resource: HasMetadata?): Boolean {
	if (resource == null) {
		return false
	} else if (this.metadata?.namespace == null
		&& resource.metadata?.namespace == null) {
		return true
	}
	return this.metadata?.namespace == resource.metadata?.namespace
}

fun HasMetadata.isSameKind(resource: HasMetadata?): Boolean {
	if (resource == null) {
		return false
	} else if (this.kind == null
		&& resource.kind == null) {
		return true
	}
	return this.kind == resource.kind
}

/**
 * Returns `true` if this resource is a newer version than the given resource.
 * Returns `false` otherwise. If this resource has a `null` resourceVersion the result is always `false`.
 * If the given resource has a `null` resourceVersion while this hasn't, it'll return `true`.
 *
 * @see [io.fabric8.kubernetes.api.model.ObjectMeta.resourceVersion]
 */
fun HasMetadata.isNewerVersionThan(resource: HasMetadata?): Boolean {
	if (resource == null
		|| !isSameResource(resource)) {
		return false
	}
	val thisVersion = this.metadata?.resourceVersion?.toIntOrNull() ?: return false
	val thatVersion = resource.metadata?.resourceVersion?.toIntOrNull() ?: return true
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
 * If there is no apiGroup value, then only the apiVersion is returned.
 */
fun getApiVersion(apiGroup: String?, apiVersion: String): String {
	return if (apiGroup != null) {
		"$apiGroup$API_GROUP_VERSION_DELIMITER$apiVersion"
	} else {
		apiVersion
	}
}

/**
 * Returns the apiGroup and apiVersion of the given [HasMetadata].
 *
 * @param resource the [HasMetadata] whose apiGroup and apiVersion should be returned
 * @return the agiGroup and apiVersion of the given [HasMetadata]
 *
 * @see [io.fabric8.kubernetes.api.model.HasMetadata.getApiVersion]
 */
fun getApiGroupAndVersion(resource: HasMetadata): Pair<String?, String> {
    val split = resource.apiVersion.split(API_GROUP_VERSION_DELIMITER)
    return if (split.size == 1) {
        Pair(null, split[0])
    } else {
        Pair(split[0], split[1])
    }
}

/**
 * Returns `true` if the given [HasMetadata] is matching the [CustomResourceDefinitionSpec]
 * of the given [CustomResourceDefinition] in
 * - group
 * - version
 * - kind
 *
 * @param resource the [HasMetadata] to check against the given definition
 * @param definition the [CustomResourceDefinition] to check against the given resource
 * @return true if the given resource is matching the given definition
 *
 * @see HasMetadata
 * @see CustomResourceDefinition
 */
fun isMatchingSpec(resource: HasMetadata, definition: CustomResourceDefinition): Boolean {
	val groupAndVersion = getApiGroupAndVersion(resource)
	return isMatchingSpec(resource.kind, groupAndVersion.first, groupAndVersion.second, definition)
}

/**
 * Returns `true` if the given [HasMetadata] is matching the given
 * - kind
 * - group
 * - version
 *
 * @param kind the kind to check against the given definition
 * @param apiGroup the apiGroup to check against the given definition
 * @param apiVersion the apiVersion to check against the given definition
 * @param definition the [CustomResourceDefinition] to check against the given resource
 * @return true if the given resource is matching the given kind, apiGroup and apiVersion
 *
 * @see HasMetadata
 * @see CustomResourceDefinition
 */
fun isMatchingSpec(kind: String, apiGroup: String?, apiVersion: String, definition: CustomResourceDefinition): Boolean {
	return definition.spec.names.kind == kind
			&& definition.spec.group == apiGroup
			&& definition.spec.versions.find { it.name == apiVersion } != null
}

/**
 * Returns the version for given [CustomResourceDefinitionSpec].
 * The version with the highest priority is chosen if there are several available.
 *
 * @param spec the [CustomResourceDefinitionSpec] to get the version from
 *
 * @return the version for the given [CustomResourceDefinitionSpec]
 */
fun getHighestPriorityVersion(spec: CustomResourceDefinitionSpec): String? {
	val versions = spec.versions.map { it.name }
	val version = KubernetesVersionPriority.highestPriority(versions)
	if (version == null) {
		logger<CustomResourceDefinitionSpec>().warn(
			"Could not find version with highest priority in ${spec.group}/${spec.names.kind}.")
	}
	return version
}

/**
 * Returns a [CustomResourceDefinitionContext] for the given [CustomResourceDefinition].
 * The version with the highest priority among the available ones is used.
 *
 * @param definition [CustomResourceDefinition] to create the context for
 *
 * @return the [CustomResourceDefinitionContext] for the given [CustomResourceDefinition]
 *
 * @see [getHighestPriorityVersion]
 */
fun createContext(definition: CustomResourceDefinition): CustomResourceDefinitionContext {
	return CustomResourceDefinitionContext.Builder()
		.withGroup(definition.spec.group)
		.withVersion(getHighestPriorityVersion(definition.spec)) // use version with highest priority
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

