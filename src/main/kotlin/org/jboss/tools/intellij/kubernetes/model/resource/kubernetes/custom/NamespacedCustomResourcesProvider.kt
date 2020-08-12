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

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind

class NamespacedCustomResourcesProvider(
		definition: CustomResourceDefinition,
		namespace: String?,
		client: KubernetesClient)
    : NamespacedResourcesProvider<GenericResource, KubernetesClient>(namespace, client) {

    override val kind = ResourceKind.new(definition.spec)
    private val context: CustomResourceDefinitionContext = CustomResourceDefinitionContext.fromCrd(definition)

    override fun loadAllResources(namespace: String): List<GenericResource> {
        val resourcesList = client.customResource(context).list(namespace)
        return GenericResourceFactory.createResources(resourcesList)
    }

    override fun getWatchable(): () -> Watchable<Watch, Watcher<GenericResource>>? {
        if (namespace == null) {
            return { null }
        }
        return {
			GenericResourceWatchable { options, customResourceWatcher ->
				val watchable = client.customResource(context)
				watchable.watch(namespace, null, null, options, customResourceWatcher)
			}
        }
    }
}