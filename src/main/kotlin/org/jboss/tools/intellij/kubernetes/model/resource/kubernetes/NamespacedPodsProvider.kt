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

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind

open class NamespacedPodsProvider(client: KubernetesClient)
    : NamespacedResourcesProvider<Pod, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.new(Pod::class.java)
    }

    override val kind = KIND

    override fun getLoadOperation(namespace: String): () -> Watchable<Watch, Watcher<Pod>>? {
        return { client.pods().inNamespace(namespace) }
    }
}
