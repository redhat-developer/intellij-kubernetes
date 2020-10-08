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
import io.fabric8.kubernetes.model.annotation.ApiGroup
import io.fabric8.kubernetes.model.annotation.ApiVersion
import io.fabric8.kubernetes.model.util.Helper

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
