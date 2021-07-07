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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AbstractResourceFactory
import io.fabric8.kubernetes.client.utils.Serialization

object GenericCustomResourceFactory: AbstractResourceFactory<GenericCustomResource>() {

	const val SPEC = "spec"

	override fun createResource(item: Map<String, Any?>): GenericCustomResource {
		@Suppress("UNCHECKED_CAST")
		return GenericCustomResource(
			item[KIND] as? String,
			item[API_VERSION] as? String,
			createObjectMetadata(item[METADATA] as? Map<String, Any?>),
			item[SPEC] as? Map<String, Any?>?
		)
	}

	override fun createResource(node: JsonNode): GenericCustomResource {
		return GenericCustomResource(
			node.get(KIND).asText(),
			node.get(API_VERSION).asText(),
			createObjectMetadata(node.get(METADATA)),
			createSpec(node.get(SPEC))
		)
	}

	private fun createSpec(node: JsonNode?): Map<String, Any?> {
		return Serialization.jsonMapper().convertValue(node, object : TypeReference<Map<String, Any>>() {})
	}
}