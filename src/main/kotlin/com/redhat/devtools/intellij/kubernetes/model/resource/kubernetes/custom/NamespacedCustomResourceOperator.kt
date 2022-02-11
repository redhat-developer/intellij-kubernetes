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

import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.Serialization

open class NamespacedCustomResourceOperator(
	override val kind: ResourceKind<GenericCustomResource>,
	context: CustomResourceDefinitionContext,
	namespace: String?,
	client: KubernetesClient
) : NamespacedResourceOperator<GenericCustomResource, KubernetesClient>(client, namespace), INamespacedResourceOperator<GenericCustomResource, KubernetesClient> {

    private val operation = CustomResourceRawOperation(client, context)

    override fun loadAllResources(namespace: String): List<GenericCustomResource> {
        val resourcesList = operation.get().list(namespace)
        return GenericCustomResourceFactory.createResources(resourcesList)
    }

    override fun watchAll(watcher: Watcher<in GenericCustomResource>): Watch? {
		return watch(namespace, null, watcher)
    }

	override fun watch(resource: HasMetadata, watcher: Watcher<in GenericCustomResource>): Watch? {
		val inNamespace = resourceOrCurrentNamespace(resource)
		return watch(inNamespace, resource.metadata.name, watcher)
	}

	private fun watch(namespace: String?, name: String?, watcher: Watcher<in GenericCustomResource>): Watch? {
		if (namespace == null) {
			return null
		}
		@Suppress("UNCHECKED_CAST")
		val typedWatcher = watcher as? Watcher<GenericCustomResource> ?: return null
		val watchableWrapper = GenericCustomResourceWatchable { options, customResourceWatcher ->
			operation.get().watch(namespace, name, null, options, customResourceWatcher)
		}
		return watchableWrapper.watch(typedWatcher)
	}

	override fun delete(resources: List<HasMetadata>): Boolean {
		@Suppress("UNCHECKED_CAST")
		val toDelete = resources as? List<GenericCustomResource> ?: return false
		return toDelete.stream()
			.map { delete(it) }
			.reduce(false) { thisDelete, thatDelete -> thisDelete || thatDelete }
	}

	private fun delete(resource: HasMetadata): Boolean {
		val inNamespace = resourceOrCurrentNamespace(resource)
		operation.get().delete(inNamespace, resource.metadata.name)
		return true
	}

	override fun replace(resource: HasMetadata): HasMetadata? {
		val toReplace = resource as? GenericCustomResource ?: return null

		val inNamespace = resourceOrCurrentNamespace(toReplace)
		return runWithoutServerSetProperties(toReplace) {
			val updated = operation.get().createOrReplace(inNamespace, Serialization.asJson(toReplace))
			GenericCustomResourceFactory.createResource(updated)
		}
	}

	override fun create(resource: HasMetadata): HasMetadata? {
		return replace(resource)
	}

	override fun get(resource: HasMetadata): HasMetadata? {
		val inNamespace = resourceOrCurrentNamespace(resource)
		val updated = operation.get().get(inNamespace, resource.metadata.name)
		return GenericCustomResourceFactory.createResource(updated)
	}

}