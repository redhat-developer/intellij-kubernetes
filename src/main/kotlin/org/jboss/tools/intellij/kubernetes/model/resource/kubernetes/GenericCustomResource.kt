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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes

import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.builder.Function
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.client.CustomResourceDoneable
import io.fabric8.kubernetes.client.CustomResourceList

class GenericCustomResource() : CustomResource(), Namespaced {

	constructor(apiVersion: String?, kind: String?, metadata: ObjectMeta?, spec: GenericCustomResourceSpec?)
			: this() {
		this.kind = kind
		this.apiVersion = apiVersion
		this.metadata = metadata
		this.spec = spec
	}

	var spec: GenericCustomResourceSpec? = null
}

class DoneableGenericCustomResource(
		resource: GenericCustomResource?,
		function: Function<GenericCustomResource?, GenericCustomResource?>?)
	: CustomResourceDoneable<GenericCustomResource?>(resource, function)


class GenericCustomResourceList: CustomResourceList<GenericCustomResource>()

@JsonDeserialize(using = JsonDeserializer.None::class)
class GenericCustomResourceSpec(val values: Map<String, Any>?) : KubernetesResource
