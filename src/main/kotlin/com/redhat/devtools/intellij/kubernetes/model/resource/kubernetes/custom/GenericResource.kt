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
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.CustomResource

class GenericResource() : CustomResource(), Namespaced {

	constructor(apiVersion: String?, kind: String?, metadata: ObjectMeta?, spec: GenericResourceSpec?) : this() {
		this.kind = kind
		this.apiVersion = apiVersion
		this.metadata = metadata
		this.spec = spec
	}

	var spec: GenericResourceSpec? = null
}

@JsonDeserialize(using = JsonDeserializer.None::class)
class GenericResourceSpec(val values: Map<String, Any?>?) : KubernetesResource
