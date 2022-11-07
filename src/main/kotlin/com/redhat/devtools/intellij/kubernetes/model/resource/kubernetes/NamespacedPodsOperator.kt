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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableExec
import com.redhat.devtools.intellij.kubernetes.model.resource.IWatchableLog
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.io.OutputStream

open class NamespacedPodsOperator(client: ClientAdapter<out KubernetesClient>):
    NamespacedResourceOperator<Pod, KubernetesClient>(client.get()),
    IWatchableLog<Pod>,
    IWatchableExec<Pod> {

    companion object {
        val KIND = ResourceKind.create(Pod::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<Pod> {
        return client.pods()
    }

    override fun watchLog(container: Container, resource: Pod, out: OutputStream): LogWatch? {
        return super.watchLog(container, resource, out)
    }

    override fun watchExec(container: Container, resource: Pod, listener: ExecListener): ExecWatch? {
        return super.watchExec(container, resource, listener)
    }
}
