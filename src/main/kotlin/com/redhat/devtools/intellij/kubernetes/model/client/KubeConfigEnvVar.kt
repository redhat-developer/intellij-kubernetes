/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.client

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil

object KubeConfigEnvVar {

    private const val KUBECONFIG_ENV_VAR = "KUBECONFIG"

    /**
     * Copies the "KUBECONFIG" env variable and it's value to the system properties.
     * This env variable is used to list multiple config files and is supported by `kubectl`.
     *
     * example:
     * ```
     * export KUBECONFIG=${HOME}/.kube/config:${HOME}/.kube/minikube.yaml
     * ```
     * On MacOS env variables present in the shell are not present in IDEA
     * because applications that are launched from the dock don't get
     * env variables that are exported for the shell (`~/.zshrc`, `~/.bashrc`, `~/.zprofile`, etc.).
     * Therefore they are not present in [System.getProperties].
     * This method inspects the shell env variables and copies them over to the System properties.
     *
     * **See Also:** [issue #826](https://github.com/redhat-developer/intellij-kubernetes/issues/826)
     */
    fun copyToSystemProperties() {
        if (SystemInfo.isMac) {
            val kubeconfig = EnvironmentUtil.getValue(KUBECONFIG_ENV_VAR) ?: return
            System.getProperties()[KUBECONFIG_ENV_VAR] = kubeconfig
            System.getProperties()[KUBECONFIG_ENV_VAR.lowercase()] = kubeconfig
        }
    }
}