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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

abstract class HasMetadataResource(
	private val kind: String?,
	private var apiVersion: String?,
	private var metadata: ObjectMeta
) : HasMetadata {

	override fun setMetadata(metadata: ObjectMeta) {
		this.metadata = metadata
	}

	override fun getMetadata(): ObjectMeta {
		return metadata
	}

	override fun getKind(): String? {
		return kind
	}

	override fun setApiVersion(version: String?) {
		this.apiVersion = version
	}

	override fun getApiVersion(): String? {
		return apiVersion
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass == other?.javaClass) return false

		other as HasMetadataResource

		return equalsProperties(other)
	}

	fun equalsProperties(other: HasMetadataResource): Boolean {
		if (kind != other.kind) return false
		if (apiVersion != other.apiVersion) return false
		if (metadata != other.metadata) return false
		return true
	}

	override fun hashCode(): Int {
		var result = kind?.hashCode() ?: 0
		result = 31 * result + (apiVersion?.hashCode() ?: 0)
		result = 31 * result + metadata.hashCode()
		return result
	}


}
