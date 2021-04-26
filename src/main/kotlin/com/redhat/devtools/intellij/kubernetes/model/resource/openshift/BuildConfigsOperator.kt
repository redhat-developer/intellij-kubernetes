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

import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.client.OpenShiftClient
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind

class BuildConfigsOperator(client: OpenShiftClient)
    : NamespacedResourceOperator<BuildConfig, OpenShiftClient>(client) {

    companion object {
        val KIND = ResourceKind.create(BuildConfig::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<BuildConfig>? {
        return client.buildConfigs()
    }
}
