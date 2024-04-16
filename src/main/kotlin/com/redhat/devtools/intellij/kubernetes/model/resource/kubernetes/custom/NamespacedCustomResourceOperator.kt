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
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.runWithoutServerSetProperties
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext

open class NamespacedCustomResourceOperator(
	override val kind: ResourceKind<GenericKubernetesResource>,
	private val context: CustomResourceDefinitionContext,
	namespace: String?,
	client: KubernetesClient
) : NamespacedResourceOperator<GenericKubernetesResource, KubernetesClient>(client, namespace), INamespacedResourceOperator<GenericKubernetesResource, KubernetesClient> {

    override fun loadAllResources(namespace: String): List<GenericKubernetesResource> {
		return getOperation()
			?.inNamespace(namespace)
			?.list()
			?.items
			?: emptyList()
    }

    override fun watchAll(watcher: Watcher<in GenericKubernetesResource>): Watch? {
		if (namespace == null) {
			return null
		}
		@Suppress("UNCHECKED_CAST")
		val typedWatcher = watcher as? Watcher<GenericKubernetesResource>? ?: return null
		return getOperation()
				?.inNamespace(namespace)
				?.watch(typedWatcher)
    }

	override fun watch(resource: HasMetadata, watcher: Watcher<in GenericKubernetesResource>): Watch? {
		val inNamespace = resourceNamespaceOrCurrent(resource)
		@Suppress("UNCHECKED_CAST")
		val typedWatcher = watcher as? Watcher<GenericKubernetesResource>? ?: return null
		return getOperation()
			?.inNamespace(inNamespace)
			?.withName(resource.metadata.name)
			?.watch(typedWatcher)
	}

	override fun delete(resources: List<HasMetadata>, force: Boolean): Boolean {
		@Suppress("UNCHECKED_CAST")
		val toDelete = resources as? List<GenericKubernetesResource> ?: return false
		return toDelete.stream()
			.map { delete(it, force) }
			.reduce(false) { thisDelete, thatDelete -> thisDelete || thatDelete }
	}

	private fun delete(resource: HasMetadata, force: Boolean): Boolean {
		val inNamespace = resourceNamespaceOrCurrent(resource)
		val operation = getOperation()
			?.inNamespace(inNamespace)
			?.withName(resource.metadata.name)
			?: return false
		operation
        .immediate(force)
        ?.delete()
		return true
	}

	override fun replace(resource: HasMetadata): HasMetadata? {
		val toReplace = resource as? GenericKubernetesResource? ?: return null

		val inNamespace = resourceNamespaceOrCurrent(toReplace)
		return runWithoutServerSetProperties(toReplace) {
			getOperation()
				?.inNamespace(inNamespace)
				?.resource(toReplace)
				?.patch()
		}
	}

	override fun create(resource: HasMetadata): HasMetadata? {
		return replace(resource)
	}

	override fun get(resource: HasMetadata): HasMetadata? {
		val inNamespace = resourceNamespaceOrCurrent(resource)
		return getOperation()
			?.inNamespace(inNamespace)
			?.withName(resource.metadata.name)
			?.get()
	}

	override fun getOperation(): NamespacedOperation<GenericKubernetesResource>? {
		return client.genericKubernetesResources(context)
	}

}
