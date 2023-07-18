/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.client.KubernetesClientException

object KubernetesClientExceptionUtils {

    fun statusMessage(t: Throwable?): String? {
        return status(t)?.message
    }

    fun status(t: Throwable?): Status? {
        return if (t is KubernetesClientException) {
            t.status
        } else {
            null
        }
    }
}