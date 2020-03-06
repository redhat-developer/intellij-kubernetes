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

import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import org.jboss.tools.intellij.kubernetes.model.WatchableResource
import org.jboss.tools.intellij.kubernetes.model.WatchableResourceSupplier

class ProjectsProvider(client: NamespacedOpenShiftClient)
    : NonNamespacedResourcesProvider<Project, OpenShiftClient>(client) {

    companion object {
        val KIND = Project::class.java
    }

    override val kind = KIND

    override fun loadAllResources(): List<Project> {
        return client.projects().list().items
    }

    override fun getWatchableResource(): WatchableResourceSupplier? {
        return { client.projects() as WatchableResource }
    }
}
