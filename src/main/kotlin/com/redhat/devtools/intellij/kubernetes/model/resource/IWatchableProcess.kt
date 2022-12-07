/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.dsl.ContainerResource
import io.fabric8.kubernetes.client.dsl.Containerable
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.LogWatch
import io.fabric8.kubernetes.client.dsl.Resource
import java.io.Closeable
import java.io.OutputStream

interface IWatchableProcess<R: HasMetadata> : Closeable

interface IWatchableLog<R: HasMetadata>: IWatchableProcess<R> {
    fun watchLog(container: Container, resource: R, out: OutputStream): LogWatch?
}

fun <R : HasMetadata> watchLog(container: Container, out: OutputStream, operation: Resource<R>): LogWatch? {
    @Suppress("UNCHECKED_CAST")
    val op = operation as? Containerable<String, ContainerResource>
        ?: return null
    return op.inContainer(container.name)
        ?.watchLog(out)
}

interface IWatchableExec<R: HasMetadata>: IWatchableProcess<R> {
    fun watchExec(container: Container, resource: R, listener: ExecListener): ExecWatch?
}

fun <R: HasMetadata> watchExec(container: Container, listener: ExecListener, operation: Resource<R>): ExecWatch? {
    val containerId = container.name ?: return null
    @Suppress("UNCHECKED_CAST")
    val op = (operation as? Containerable<String, ContainerResource>)
        ?.inContainer(containerId)
        ?.redirectingInput()
        ?.redirectingOutput()
        ?.redirectingError()
        ?.withTTY()
        ?.usingListener(listener)
        ?: return null

    return op.exec("sh")
}
