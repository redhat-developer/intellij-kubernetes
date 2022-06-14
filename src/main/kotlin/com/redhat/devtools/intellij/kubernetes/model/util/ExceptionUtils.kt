/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.client.Config.DEFAULT_MASTER_URL
import java.net.UnknownHostException

private const val DEFAULT_KUBECLIENT_ERRORMESSAGE = "An error has occurred"

fun toTitle(e: Throwable?): String {
    return noCurrentContextMessage(e)
        ?: e?.message
        ?: e?.cause?.message
        ?: "Error"
}

fun toMessage(e: Throwable?): String {
    return noCurrentContextMessage(e)
        ?: recursiveGetMessage(e)
        ?: "Unknown Error"
}

private fun recursiveGetMessage(e: Throwable?): String? {
    if (e == null) {
        return null
    }
    val message = e.message
    if (message == null
        || message.startsWith(DEFAULT_KUBECLIENT_ERRORMESSAGE)) {
        return recursiveGetMessage(e.cause)
    }
    return message
}

fun noCurrentContextMessage(e: Throwable?): String? {
    return if (e is UnknownHostException
        && e.message == DEFAULT_MASTER_URL) {
        "No valid current context in kube config"
    } else {
        null
    }
}
