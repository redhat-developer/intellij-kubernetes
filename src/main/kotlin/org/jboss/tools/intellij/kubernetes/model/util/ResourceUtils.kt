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
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil
import io.fabric8.kubernetes.model.annotation.ApiGroup
import io.fabric8.kubernetes.model.annotation.ApiVersion
import io.fabric8.kubernetes.model.util.Helper

/**
 * returns {@code true} if the given resources are equal.
 * These are considered equal if their name, namespace and kind are equal.
 */
fun areEqual(thisResource: HasMetadata, thatResource: HasMetadata): Boolean {
	return KubernetesResourceUtil.getName(thisResource) == KubernetesResourceUtil.getName(thatResource)
			&& KubernetesResourceUtil.getNamespace(thisResource) == KubernetesResourceUtil.getNamespace(thatResource)
			&& KubernetesResourceUtil.getKind(thisResource) == KubernetesResourceUtil.getKind(thatResource)
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
	val apiGroup = Helper.getAnnotationValue(clazz, ApiGroup::class.java)
	val apiVersion = Helper.getAnnotationValue(clazz, ApiVersion::class.java)
	return if (apiGroup != null && apiGroup.isNotBlank()
			&& apiVersion != null && apiVersion.isNotBlank()) {
		getApiVersion(apiGroup, apiVersion)
	} else {
		clazz.simpleName
	}
}

/**
 * Returns the version for given apiGroup and apiVersion. Both values are concatenated and separated by '/'.
 */
fun getApiVersion(apiGroup: String, apiVersion: String) = "$apiGroup/$apiVersion"
