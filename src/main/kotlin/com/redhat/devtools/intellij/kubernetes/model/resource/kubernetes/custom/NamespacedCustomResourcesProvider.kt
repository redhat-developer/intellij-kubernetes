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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import java.util.function.Supplier

class NamespacedCustomResourcesProvider(
	definition: CustomResourceDefinition,
	namespace: String?,
	client: KubernetesClient
) : NamespacedResourcesProvider<GenericCustomResource, KubernetesClient>(namespace, client) {

    override val kind = ResourceKind.create(definition.spec)
    private val operation = CustomResourceRawOperation(client, definition)

    override fun loadAllResources(namespace: String): List<GenericCustomResource> {
        val resourcesList = operation.get().list(namespace)
        return GenericCustomResourceFactory.createResources(resourcesList)
    }

    override fun getWatchable(): Supplier<Watchable<Watcher<GenericCustomResource>>?> {
        if (namespace == null) {
            return Supplier { null }
        }
        return Supplier {
			GenericCustomResourceWatchable { options, customResourceWatcher ->
				operation.get().watch(namespace, null, null, options, customResourceWatcher)
			}
        }
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
			logger<NonNamespacedCustomResourcesProvider>()
				.info("Could not delete $kind custom resource named $name in namespace $namespace.", e)
			false
		}
	}
}