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

import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.ObjectMeta

@JsonDeserialize(using = GenericCustomResourceDeserializer::class)
class GenericCustomResource(
	private val kind: String?,
	private var apiVersion: String?,
	private var metadata: ObjectMeta,
	val spec: GenericCustomResourceSpec?
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
		this.apiVersion = apiVersion
	}

	override fun getApiVersion(): String? {
		return apiVersion
	}

}

@JsonDeserialize(using = JsonDeserializer.None::class)
class GenericCustomResourceSpec(val values: Map<String, Any?>?) : KubernetesResource
