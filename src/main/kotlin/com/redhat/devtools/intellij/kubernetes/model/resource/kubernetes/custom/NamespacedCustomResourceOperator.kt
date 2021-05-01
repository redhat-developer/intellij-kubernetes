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

import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.utils.Serialization
import io.fabric8.kubernetes.internal.KubernetesDeserializer

class NamespacedCustomResourceOperator(
	definition: CustomResourceDefinition,
	namespace: String?,
	client: KubernetesClient
) : NamespacedResourceOperator<GenericCustomResource, KubernetesClient>(namespace, client) {

    override val kind = ResourceKind.create(definition.spec)
    private val operation = CustomResourceRawOperation(client, definition)

    override fun loadAllResources(namespace: String): List<GenericCustomResource> {
        val resourcesList = operation.get().list(namespace)
        return GenericCustomResourceFactory.createResources(resourcesList)
    }

    override fun watchAll(watcher: Watcher<out HasMetadata>): Watch? {
		return watch(namespace, null, watcher)
    }

	override fun watch(resource: HasMetadata, watcher: Watcher<out HasMetadata>): Watch? {
		return watch(namespace, resource.metadata.name, watcher)
	}

	private fun watch(namespace: String?, name: String?, watcher: Watcher<out HasMetadata>): Watch? {
		if (namespace == null) {
			return null
		}
		val typedWatcher = watcher as? Watcher<GenericCustomResource> ?: return null
		val watchableWrapper = GenericCustomResourceWatchable { options, customResourceWatcher ->
			operation.get().watch(namespace, null, null, options, customResourceWatcher)
		}
		return watchableWrapper.watch(typedWatcher)
	}

	override fun delete(resources: List<HasMetadata>): Boolean {
		@Suppress("UNCHECKED_CAST")
		val toDelete = resources as? List<GenericCustomResource> ?: return false
		return toDelete.stream()
			.map { delete(it.metadata.namespace, it.metadata.name) }
			.reduce(false, { thisDelete, thatDelete -> thisDelete || thatDelete })
	}

	private fun delete(namespace: String, name: String): Boolean {
		return try {
			operation.get().delete(namespace, name)
			true
		} catch(e: KubernetesClientException) {
			logger<NonNamespacedCustomResourceOperator>()
				.info("Could not delete $kind custom resource named $name in namespace $namespace.", e)
			false
		}
	}

	override fun replace(resource: HasMetadata): HasMetadata? {
		if (namespace == null) {
			return null
		}
		return try {
			val updated = operation.get().createOrReplace(namespace, Serialization.asJson(resource))
			GenericCustomResourceFactory.createResource(updated)
		} catch(e: KubernetesClientException) {
			logger<NonNamespacedCustomResourceOperator>()
				.info("Could not replace $kind custom resource named ${resource.metadata.name} in namespace $namespace.", e)
			null
		}
	}
}