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

import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider

open class NodesProvider(client: KubernetesClient)
    : NonNamespacedResourcesProvider<Node, KubernetesClient>(client) {

    companion object {
        val KIND = Node::class.java;
    }

    override val kind = KIND

    override fun loadAllResources(): List<Node> {
        return client.nodes().list().items
    }

    override fun getWatchableResource(): () -> Watchable<Watch, Watcher<Node>>? {
        return { client.nodes() }
    }
}
