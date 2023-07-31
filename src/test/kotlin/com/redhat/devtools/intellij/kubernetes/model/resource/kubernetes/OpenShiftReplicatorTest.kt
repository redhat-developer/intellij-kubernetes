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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.OpenShiftReplicas.OpenShiftReplicator
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import io.fabric8.openshift.api.model.DeploymentConfigSpec
import org.junit.Test

class OpenShiftReplicatorTest {

    @Test
    fun `#replicas should return Deployment replicas`() {
        // given
        val spec: DeploymentConfigSpec = mock()
        val deployment = DeploymentConfigBuilder()
            .withSpec(spec)
            .build()
        val replicator = OpenShiftReplicator(deployment)
        // when
        replicator.replicas
        // then
        verify(spec).replicas
    }

    @Test
    fun `#replicas = XX should set DeploymentConfig replicas`() {
        // given
        val spec: DeploymentConfigSpec = mock()
        val deploymentConfig: DeploymentConfig = mock {
            on { this.spec } doReturn spec
        }
        val replicator = OpenShiftReplicator(deploymentConfig)
        val replicas = 42
        // when
        replicator.replicas = replicas
        // then
        verify(spec).replicas = replicas
    }

}