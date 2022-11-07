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
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableExec
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableLog
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.client.OpenShiftClient
import java.io.OutputStream

class BuildsOperator(client: ClientAdapter<out OpenShiftClient>)
    : NamespacedResourceOperator<Build, OpenShiftClient>(client.get()), IWatchableLog<Build>, IWatchableExec<Build> {

    companion object {
        val KIND = ResourceKind.create(Build::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<Build> {
        return client.builds()
    }

    override fun watchLog(container: Container, resource: Build, out: OutputStream): LogWatch? {
        return super.watchLog(container, resource, out)
    }

    override fun watchExec(container: Container, resource: Build, listener: ExecListener): ExecWatch? {
        return super.watchExec(container, resource, listener)
    }

}
