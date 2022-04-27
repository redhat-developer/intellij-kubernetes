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

import com.fasterxml.jackson.databind.JsonNode
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory

object HasMetadataResourceFactory: AbstractResourceFactory<HasMetadataResource>() {

	const val SPEC = "spec"

	@Suppress("UNCHECKED_CAST")
	override fun createResource(item: Map<String, Any?>?): HasMetadataResource? {
		if (item == null) {
			return null
		}
		return HasMetadataResource(
			item[KIND] as? String,
			item[API_VERSION] as? String,
			createObjectMetadata(item[METADATA] as? Map<String, Any?>)
		)
	}

	override fun createResource(node: JsonNode): HasMetadataResource {
		return HasMetadataResource(
			node.get(KIND)?.asText(),
			node.get(API_VERSION)?.asText(),
			createObjectMetadata(node.get(METADATA))
		)
	}
}