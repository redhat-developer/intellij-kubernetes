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
import io.fabric8.kubernetes.client.dsl.LogWatch
import io.fabric8.kubernetes.client.dsl.Loggable
import io.fabric8.kubernetes.client.dsl.Resource
import io.fabric8.kubernetes.client.utils.PodOperationUtil.watchLog
import java.io.OutputStream

interface ILogWatcher<R: HasMetadata> {

    fun watchLog(container: Container?, resource: R, out: OutputStream): LogWatch?

}

class LogWatcher<R: HasMetadata> {

    fun watch(container: Container?, resource: R, out: OutputStream, operation: Resource<R>): LogWatch? {
        val containerToLog = container ?: getFirstContainer(resource)
        return if (containerToLog == null) {
            @Suppress("UNCHECKED_CAST")
            val op = operation as? Loggable<LogWatch>
                ?: return null
            op.watchLog(out)
        } else {
            @Suppress("UNCHECKED_CAST")
            val op = operation as? Containerable<String, ContainerResource<LogWatch, *, *, *, *, *, *, *, *, *>>
                ?: return null
            op.inContainer(containerToLog.name)
                ?.watchLog(out)
        }
    }

}
