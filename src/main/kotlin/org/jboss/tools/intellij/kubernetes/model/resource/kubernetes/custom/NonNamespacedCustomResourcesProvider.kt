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
import org.jboss.tools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.WatchableAndListable

class NonNamespacedCustomResourcesProvider(
        definition: CustomResourceDefinition,
        client: KubernetesClient)
    : NonNamespacedResourcesProvider<GenericResource, KubernetesClient>(client) {

    override val kind = ResourceKind.new(definition.spec)
    private val context: CustomResourceDefinitionContext = CustomResourceDefinitionContext.fromCrd(definition)

    override fun loadAllResources(): List<GenericResource> {
        val resourcesList = client.customResource(context).list()
        return GenericResourceFactory.createResources(resourcesList)
    }

    override fun getWatchable(): () -> Watchable<Watch, Watcher<GenericResource>>? {
        return {
            GenericResourceWatchable { options, customResourceWatcher ->
                val watchable = client.customResource(context)
                watchable.watch(null, null, null, options, customResourceWatcher)
            }
        }
    }

    override fun getOperation(): () -> WatchableAndListable<GenericResource> {
        return { null }
    }

}