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
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.NonCachingSingleResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.*
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.OpenShiftReplicas
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.*
import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import io.fabric8.openshift.api.model.DeploymentConfigSpec
import org.junit.Test

class OpenShiftReplicasTest {

    private val operator: NonCachingSingleResourceOperator = mock()
    private val getAllResources: ResourcesRetrieval = mock()
    private val kubeReplicas = OpenShiftReplicas(operator, getAllResources)

    @Test
    fun `#get should return replicas for given DeploymentConfig`() {
        // given
        val spec: DeploymentSpec = mock()
        val deployment = DeploymentBuilder()
            .withSpec(spec)
            .build()
        // when
        kubeReplicas.get(deployment)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return Deployment replicas for given pod`() {
        // given
        val pod = createPod(
            mapOf(
                "jedi" to "yoda",
                "planet" to "Dagobah"
            )
        )
        val spec: DeploymentConfigSpec = createDeploymentConfigSpec(mapOf("jedi" to "yoda"))
        val replicator = DeploymentConfigBuilder()
            .withSpec(spec)
            .build()
        mockGetAllResources(DeploymentConfigsOperator.KIND, listOf(
            mock(),
            replicator,
            mock())
        )
        // when
        kubeReplicas.get(pod)
        // then
        verify(spec).replicas
    }

    private fun createDeploymentConfigSpec(selectorLabels: Map<String, String>): DeploymentConfigSpec {
        return mock {
            whenever(mock.replicas) doReturn 22
            whenever(mock.selector) doReturn selectorLabels
        }
    }

    private fun <T: HasMetadata> mockGetAllResources(kind: ResourceKind<T>, resources: Collection<T>) {
        doReturn(resources)
            .whenever(getAllResources).getAll(kind, IActiveContext.ResourcesIn.CURRENT_NAMESPACE)
    }

    private fun createPod(labels: Map<String, String>): Pod {
        return PodBuilder()
            .withNewMetadata()
                .withLabels<String, String>(labels)
            .endMetadata()
            .build()
    }
}