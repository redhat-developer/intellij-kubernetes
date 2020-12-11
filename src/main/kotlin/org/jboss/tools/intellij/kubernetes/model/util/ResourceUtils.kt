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
package org.jboss.tools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.model.annotation.ApiGroup
import io.fabric8.kubernetes.model.annotation.ApiVersion
import io.fabric8.kubernetes.model.util.Helper
import org.jboss.tools.intellij.kubernetes.model.resource.KubernetesVersionPriority
import java.util.stream.Collectors

/**
 * returns {@code true} if the given resource has the same uid as this resource. Returns {@code false} otherwise.
 */
fun HasMetadata.sameUid(resource: HasMetadata): Boolean {
	return this.metadata.uid == resource.metadata.uid
}

fun HasMetadata.isUpdated(resource: HasMetadata): Boolean {
	return try {
		val thisVersion = this.metadata.resourceVersion.toInt()
		val thatVersion = resource.metadata.resourceVersion.toInt()
		thisVersion < thatVersion
	} catch(e: NumberFormatException) {
		false
	}
}

/**
 * Returns the version for a given subclass of HasMetadata.
 * The version is built of the apiGroup and apiVersion that are annotated in the HasMetadata subclasses.
 *
 * @see HasMetadata.getApiVersion
 * @see io.fabric8.kubernetes.model.annotation.ApiVersion (annotation)
 * @see io.fabric8.kubernetes.model.annotation.ApiGroup (annotation)
 */
fun getApiVersion(clazz: Class<out HasMetadata>): String {
	val apiVersion = Helper.getAnnotationValue(clazz, ApiVersion::class.java)
	return if (!apiVersion.isNullOrBlank()) {
		val apiGroup = Helper.getAnnotationValue(clazz, ApiGroup::class.java)
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
		.build();
}

fun setDeleted(timestamp: String, resource: HasMetadata) {
	resource.metadata.deletionTimestamp = timestamp
}

fun isDeleted(resource: HasMetadata?): Boolean {
	return resource?.metadata?.deletionTimestamp != null
}

/**
 * Returns a message listing the given resources by name while using ',' as delimiter.
 * Duplicate resources are ignored. Names that are longer than 20 characters are trimmed.
 *
 * @see toMessage
 * @see trim
 */
fun toMessage(resources: Collection<HasMetadata>): String {
	return resources.stream()
		.distinct()
		.map { toMessage(it) }
		.collect(Collectors.joining(",\n"))
}

fun toMessage(resource: HasMetadata): String {
	return "${resource.kind} \"${trimName(resource.metadata.name, 20)}\""
}

/**
 * Trims a string to the given length.
 * The following rules are applied:
 * <ul>
 *		<li>if the string is shorter than the given length, the message is returned as is</li>
 *		<li>if the string is longer than the given length of <= 3, the given length is returned</li>
 *		<li>if the string is longer than the given length of <= 6, the given message is trimmed
 *		to length - 3 and "..." is appended</li>
 *		<li>if the string is larger than the given length, the given message is trimmed to the given
 *		length - 3, "..." is appended and the last 3 characters of the message are appended</li>
 * </ul>
 */
fun trimName(name: String, length: Int): String {
	return when {
		length <= name.length -> name
		length <= 3 -> name.substring(0, length)
		length <= 6 -> "${name.take(length - 3)}..."
		else -> "${name.take(length - 6)}..." +
				"${name.takeLast(3)}"
	}
}
