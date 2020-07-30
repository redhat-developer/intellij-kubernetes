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

import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.WatchListDeletable
import org.jboss.tools.intellij.kubernetes.model.resource.NonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.WatchableAndListable

class AllPodsProvider(client: KubernetesClient)
    : NonNamespacedResourcesProvider<Pod, KubernetesClient>(client) {

    companion object {
        val KIND = ResourceKind.new(Pod::class.java)
    }

    override val kind = KIND

    override fun getOperation(): () -> WatchableAndListable<Pod> {
        return { client.pods().inAnyNamespace() }
    }
}
