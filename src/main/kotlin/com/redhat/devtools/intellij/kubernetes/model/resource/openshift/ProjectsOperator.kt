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
package com.redhat.devtools.intellij.kubernetes.model.resource.openshift

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.OpenShiftClient

class ProjectsOperator(client: ClientAdapter<out OpenShiftClient>)
    : NonNamespacedResourceOperator<Project, OpenShiftClient>(client.get()) {

    companion object {
        val KIND = ResourceKind.create(Project::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NonNamespacedOperation<Project> {
        return client.projects()
    }
}
