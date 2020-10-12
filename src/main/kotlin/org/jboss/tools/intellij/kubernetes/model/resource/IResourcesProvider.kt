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
package org.jboss.tools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.WatchListDeletable
import io.fabric8.kubernetes.client.dsl.Watchable

typealias WatchableAndListable<R> = WatchListDeletable<R, out KubernetesResourceList<R>, Boolean, Watch, Watcher<R>>?

interface IResourcesProvider<R: HasMetadata> {
    val kind: ResourceKind<R>
    fun getAllResources(): Collection<R>
    fun getWatchable(): () -> Watchable<Watch, Watcher<R>>?
    fun invalidate()
    fun replace(resource: HasMetadata): Boolean
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
}