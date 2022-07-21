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

import com.redhat.devtools.intellij.kubernetes.model.util.getFirstContainer
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.dsl.ContainerResource
import io.fabric8.kubernetes.client.dsl.Containerable
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.Execable
import io.fabric8.kubernetes.client.dsl.Resource

interface IExecWatcher<R: HasMetadata> {
    fun watchExec(container: Container?, resource: R): ExecWatch?

}

class ExecWatcher<R: HasMetadata> {
    fun watch(container: Container?, resource: R, operation: Resource<R>): ExecWatch? {
        val containerId = container?.name ?: getFirstContainer(resource)?.name ?: return null
        @Suppress("UNCHECKED_CAST")
        val op = (operation as? Containerable<String, ContainerResource<*, *, *, *, *, *, *, *, *, *>>?)
            ?.inContainer(containerId)
            ?.redirectingInput()
            ?.redirectingOutput()
            ?.redirectingError()
            ?.withTTY() as? Execable<String, ExecWatch>
            ?: return null

        return op.exec("sh")
    }

}
