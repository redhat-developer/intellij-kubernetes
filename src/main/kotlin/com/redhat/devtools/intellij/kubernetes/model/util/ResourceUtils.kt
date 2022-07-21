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
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.utils.ApiVersionUtil
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
fun HasMetadata.isOutdated(resource: HasMetadata?): Boolean {
	if (resource == null
		|| !isSameResource(resource)) {
		return false
	}
	val thisVersion = this.metadata?.resourceVersion?.toIntOrNull()
	val thatVersion = resource.metadata?.resourceVersion?.toIntOrNull()
	return if (thisVersion == null) {
		thatVersion != null
	} else {
		if (thatVersion == null) {
			false
		} else {
			thisVersion > thatVersion
		}
	}
}

fun String.isGreaterIntThan(other: String?): Boolean {
	val thisInt = this.toIntOrNull() ?: return other?.toIntOrNull() != null
	val thatInt = other?.toIntOrNull() ?: return true
	return  thisInt > thatInt
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
	val group = ApiVersionUtil.trimGroupOrNull(resource.apiVersion)
	val version = ApiVersionUtil.trimVersion(resource.apiVersion)
	return Pair(group, version)
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
 * @see com.redhat.devtools.intellij.kubernetes.model.util.toMessage
 * @see trimWithEllipsis
 */
fun toMessage(resources: Collection<HasMetadata>, maxLength: Int = -1): String {
	return resources.stream()
		.distinct()
		.map { toMessage(it, maxLength) }
		.collect(Collectors.joining(",\n"))
}

fun toMessage(resource: HasMetadata, maxLength: Int = -1): String {
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
			"Could not find version with highest priority in ${spec.group}/${spec.names.kind}."
		)
	}
	return version
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

fun <T> createResource(jsonYaml: String, clazz: Class<T>): T {
	return Serialization.unmarshal(jsonYaml, clazz)
}

fun <R: HasMetadata?> runWithoutServerSetProperties(resource: R, operation: () -> R): R {
	// remove properties
	val resourceVersion = removeResourceVersion(resource)
	val uid = removeUid(resource)
	val value = operation.invoke()
	// restore properties
	setResourceVersion(resourceVersion, resource)
	setUid(uid, resource)
	return value
}

fun <T: HasMetadata?, R: Any?> runWithoutServerSetProperties(thisResource: T?, thatResource: T?, operation: () -> R): R {
	// remove properties
	val thisResourceVersion = removeResourceVersion(thisResource)
	val thatResourceVersion = removeResourceVersion(thatResource)
	val thisUid = removeUid(thisResource)
	val thatUid = removeUid(thatResource)
	val result = operation.invoke()
	// restore properties
	setResourceVersion(thisResourceVersion, thisResource)
	setResourceVersion(thatResourceVersion, thatResource)
	setUid(thisUid, thisResource)
	setUid(thatUid, thatResource)
	return result
}

fun <R: HasMetadata?> removeResourceVersion(resource: R): String? {
	if (resource == null) {
		return null
	}
	val value = resource.metadata.resourceVersion
	resource.metadata.resourceVersion = null
	return value
}

fun <R: HasMetadata?> setResourceVersion(resourceVersion: String?, resource: R) {
	if (resource == null) {
		return
	}
	resource.metadata.resourceVersion = resourceVersion
}

fun <R: HasMetadata?> removeUid(resource: R): String? {
	if (resource == null) {
		return null
	}
	val value = resource.metadata.uid
	resource.metadata.uid = null
	return value
}

fun <R: HasMetadata?> setUid(uid: String?, resource: R) {
	if (resource == null) {
		return
	}
	resource.metadata.uid = uid
}

/**
 * Returns `true` if the given resource has a non-empty property `generateName`. Returns `false` otherwise.
 *
 * @param resource the resource to check if it has a generateName property
 *
 * @see [io.fabric8.kubernetes.api.model.ObjectMeta.generateName]
 */
fun <R: HasMetadata> hasGenerateName(resource: R): Boolean {
	return true == resource.metadata.generateName?.isNotEmpty()
}

/**
 * Returns `true` if the given resource has a non-empty property `name`. Returns `false` otherwise.
 *
 * @param resource the resource to check if it has a name property
 *
 * @see [io.fabric8.kubernetes.api.model.ObjectMeta.name]
 */
fun <R: HasMetadata> hasName(resource: R): Boolean {
	return true == resource.metadata.name?.isNotEmpty()
}
