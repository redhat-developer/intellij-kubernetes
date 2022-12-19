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
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.client.KubernetesClientException
import java.net.HttpURLConnection

fun KubernetesClientException.isNotFound(): Boolean {
    return code == HttpURLConnection.HTTP_NOT_FOUND
}

fun KubernetesClientException.isForbidden(): Boolean {
    return code == HttpURLConnection.HTTP_FORBIDDEN
}

fun KubernetesClientException.isUnauthorized(): Boolean {
    return code == HttpURLConnection.HTTP_UNAUTHORIZED
}

fun KubernetesClientException.isUnsupported(): Boolean {
    return code == HttpURLConnection.HTTP_UNSUPPORTED_TYPE
}
