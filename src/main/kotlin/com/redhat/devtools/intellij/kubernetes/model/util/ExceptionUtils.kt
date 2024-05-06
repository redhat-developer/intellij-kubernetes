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
import io.fabric8.kubernetes.client.KubernetesClientException
import java.net.UnknownHostException

fun toTitle(e: Throwable?): String {
    return noCurrentContextMessage(e)
        ?: unknownHostMessage(e)
        ?: unauthorizedMessage(e)
        ?: extractMessage(e?.message)
        ?: extractMessage(e?.cause?.message)
        ?: "Error"
}

fun toMessage(e: Throwable?): String {
    return noCurrentContextMessage(e)
        ?: unknownHostMessage(e)
        ?: unauthorizedMessage(e)
        ?: extractMessage(recursiveGetMessage(e))
        ?: "Unknown Error."
}

private fun recursiveGetMessage(e: Throwable?): String? {
    if (e == null) {
        return null
    }
    val message = e.message
    if (message == null
        || isAnErrorHasOccurred(message)
        || isOperation(message)
    ) {
        return recursiveGetMessage(e.cause)
    }
    return message
}

private fun isOperation(message: String): Boolean {
    /**
     * ex. minikube: KubernetesClientException:
     * "Operation: [list]  for kind: [Service]  with name: [null]  in namespace: [default]  failed."
     */
    return message.startsWith("Operation: ")
            && message.endsWith("failed.")
}

private fun isAnErrorHasOccurred(message: String) = message.startsWith("An error has occurred")

private fun extractMessage(message: String?): String? {
    return if (message == null) {
        null
    } else if (message.contains("Message: ")) {
        /**
         * ex. OpenShift: KubernetesClientException:
         * "Failure executing: GET at: https://api.sandbox-m3.1530.p1.openshiftapps.com:6443/apis/project.openshift.io/v1/projects.
         * Message: Unauthorized! Token may have expired! Please log-in again. Unauthorized."
         */
        message.substringAfter("Message: ", message)
    } else {
        message
    }
}

fun noCurrentContextMessage(e: Throwable?): String? {
    return if (e is UnknownHostException
        && e.message == DEFAULT_MASTER_URL) {
        "No valid current context in kube config"
    } else {
        null
    }
}

private fun unknownHostMessage(e: Throwable?): String? {
    val unknownHostException = recursiveGetThrowable(e) { throwable ->
        throwable is UnknownHostException
    }
    return if (unknownHostException is UnknownHostException) {
        val host = getHost(unknownHostException)
        "Unreachable cluster${
            if (host != null) {
                " at$host."
            } else {
                "."
            }
        }"
    } else {
        null
    }
}

private fun getHost(e: UnknownHostException): String? {
    val portions = e.message?.split(':') ?: return null
    return if (1 < portions.size) {
        portions[1]
    } else {
        null
    }
}

private fun unauthorizedMessage(e: Throwable?): String? {
    val unauthorizedException = recursiveGetThrowable(e) { throwable ->
        throwable is KubernetesClientException
                && throwable.isUnauthorized()
    }
    return if (unauthorizedException != null) {
        "Unauthorized. Verify username and password, refresh token, etc."
    } else {
        null
    }
}

private fun recursiveGetThrowable(e: Throwable?, predicate: (e: Throwable?) -> Boolean): Throwable? {
    return if (e == null
        || e == e.cause
        || predicate.invoke(e)) {
        e
    } else {
        recursiveGetThrowable(e.cause, predicate)
    }
}
