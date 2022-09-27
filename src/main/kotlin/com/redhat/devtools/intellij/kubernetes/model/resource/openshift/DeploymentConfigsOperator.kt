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
package com.redhat.devtools.intellij.kubernetes.model.resource.openshift

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.client.OpenShiftClient

class DeploymentConfigsOperator(client: ClientAdapter<out OpenShiftClient>)
    : NamespacedResourceOperator<DeploymentConfig, OpenShiftClient>(client.get()) {

    companion object {
        val KIND = ResourceKind.create(DeploymentConfig::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<DeploymentConfig> {
        return client.deploymentConfigs()
    }
}
