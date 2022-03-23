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
package com.redhat.devtools.intellij.kubernetes.model.mocks

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder

object Fakes {

    private val defaultNamespace = "wildWest"
    private val defaultApiVersion = "v1"

    fun deployment(name: String, namespace: String = defaultNamespace, apiVersion: String = defaultApiVersion): Deployment {
        return DeploymentBuilder()
            .withApiVersion(apiVersion)
            .editOrNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .endMetadata()
            .build()
    }

    fun pod(name: String, namespace: String = defaultNamespace, apiVersion: String = defaultApiVersion): Pod {
        return PodBuilder()
            .withApiVersion(apiVersion)
            .editOrNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .endMetadata()
            .build()
    }

}