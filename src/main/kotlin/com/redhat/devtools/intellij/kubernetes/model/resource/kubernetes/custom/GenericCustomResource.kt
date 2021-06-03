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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import io.fabric8.kubernetes.api.model.ObjectMeta

@JsonDeserialize(using = GenericCustomResourceDeserializer::class)
class GenericCustomResource(
	kind: String?,
	apiVersion: String?,
	metadata: ObjectMeta,
	val spec: Map<String, Any?>?
) : HasMetadataResource(kind, apiVersion, metadata) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as GenericCustomResource

		if (!equalsProperties(other)) return false
		if (spec != other.spec) return false

		return true
	}

	override fun hashCode(): Int {
		var result = kind?.hashCode() ?: 0
		result = 31 * result + (apiVersion?.hashCode() ?: 0)
		result = 31 * result + metadata.hashCode()
		result = 31 * result + (spec?.hashCode() ?: 0)
		return result
	}


}
