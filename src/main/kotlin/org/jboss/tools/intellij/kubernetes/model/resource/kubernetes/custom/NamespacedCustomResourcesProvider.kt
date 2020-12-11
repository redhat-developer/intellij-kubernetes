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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import java.util.function.Supplier

class NamespacedCustomResourcesProvider(
	definition: CustomResourceDefinition,
	namespace: String?,
	client: KubernetesClient
) : NamespacedResourcesProvider<GenericResource, KubernetesClient>(namespace, client) {

    override val kind = ResourceKind.create(definition.spec)
    private val operation = CustomResourceRawOperation(client, definition)

    override fun loadAllResources(namespace: String): List<GenericResource> {
        val resourcesList = operation.get().list(namespace)
        return GenericResourceFactory.createResources(resourcesList)
    }

    override fun getWatchable(): Supplier<Watchable<Watch, Watcher<GenericResource>>?> {
        if (namespace == null) {
            return Supplier { null }
        }
        return Supplier {
			GenericResourceWatchable { options, customResourceWatcher ->
				operation.get().watch(namespace, null, null, options, customResourceWatcher)
			}
        }
    }

	override fun delete(resources: List<HasMetadata>): Boolean {
		val toDelete = resources as? List<GenericResource> ?: return false
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