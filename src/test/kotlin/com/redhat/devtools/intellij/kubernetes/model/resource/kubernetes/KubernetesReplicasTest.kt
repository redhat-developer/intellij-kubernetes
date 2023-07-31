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
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.*
import io.fabric8.kubernetes.api.model.batch.v1.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KubernetesReplicasTest {

    private val operator: NonCachingSingleResourceOperator = mock()
    private val getAllResources: ResourcesRetrieval = mock()
    private val kubeReplicas = KubernetesReplicas(operator, getAllResources)

    @Test
    fun `#get should return null for given Job`() {
        // given
        val job: Job = mock() // unsupported kind
        // when
        val replicator = kubeReplicas.get(job)
        // then
        assertThat(replicator).isNull()
    }

    @Test
    fun `#get should return replicas for given Deployment`() {
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
    fun `#get should return replicas for given ReplicationController`() {
        // given
        val spec: ReplicationControllerSpec = mock()
        val replicationController = ReplicationControllerBuilder()
            .withSpec(spec)
            .build()
        // when
        kubeReplicas.get(replicationController)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return replicas for given ReplicaSet`() {
        // given
        val spec: ReplicaSetSpec = mock()
        val replicaSet = ReplicaSetBuilder()
            .withSpec(spec)
            .build()
        // when
        kubeReplicas.get(replicaSet)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return replicas for given StatefulSet`() {
        // given
        val spec: StatefulSetSpec = mock()
        val statefulSet = StatefulSetBuilder()
            .withSpec(spec)
            .build()
        // when
        kubeReplicas.get(statefulSet)
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
        val spec: DeploymentSpec = createDeploymentSpec(mapOf("jedi" to "yoda"))
        val replicator = DeploymentBuilder()
            .withSpec(spec)
            .build()
        mockGetAllResources(DeploymentsOperator.KIND, listOf(
            replicator,
            mock())
        )
        // when
        kubeReplicas.get(pod)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return null for given pod which isn't matching deployment matchLabels`() {
        // given
        val pod = createPod(
            emptyMap() // no labels
        )
        val spec: DeploymentSpec = createDeploymentSpec(mapOf("jedi" to "yoda"))
        val deployment = DeploymentBuilder().withSpec(spec).build()
        mockGetAllResources(DeploymentsOperator.KIND, listOf(
            deployment,
            mock())
        )
        // when
        val found = kubeReplicas.get(pod)
        // then
        assertThat(found).isNull()
    }

    @Test
    fun `#get should return ReplicationController replicas for given pod`() {
        // given
        val pod = createPod(
                mapOf(
                    "jedi" to "yoda",
                    "planet" to "Dagobah"
                )
        )
        val spec: ReplicationControllerSpec = createReplicationControllerSpec(mapOf("jedi" to "yoda"))
        val replicatingController = ReplicationControllerBuilder()
            .withSpec(spec)
            .build()
        mockGetAllResources(ReplicationControllersOperator.KIND, listOf(
            mock(),
            replicatingController,
            mock())
        )
        // when
        kubeReplicas.get(pod)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return ReplicaSet replicas for given pod`() {
        // given
        val pod = createPod(
            mapOf(
                "jedi" to "yoda",
                "planet" to "Dagobah"
            )
        )
        val spec = createReplicaSetSpec(
            mapOf(
                "jedi" to "yoda"
            )
        )
        val replicaSet = ReplicaSetBuilder()
            .withSpec(spec)
            .build()
        mockGetAllResources(ReplicaSetsOperator.KIND, listOf(
            mock(),
            replicaSet,
            mock())
        )
        // when
        kubeReplicas.get(pod)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#get should return StatefulSet replicas for given pod`() {
        // given
        val pod = createPod(
            mapOf(
                "jedi" to "yoda",
                "planet" to "Dagobah"
            )
        )
        val spec = createStatefulSetSpec(
            mapOf(
                "jedi" to "yoda"
            )
        )
        val statefulSet = StatefulSetBuilder()
            .withSpec(spec)
            .build()
        mockGetAllResources(StatefulSetsOperator.KIND, listOf(
            mock(),
            statefulSet,
            mock())
        )
        // when
        kubeReplicas.get(pod)
        // then
        verify(spec).replicas
    }

    @Test
    fun `#set should set replicas to given Replicator`() {
        // given
        val replicator: Replicator = mock()
        val replicas = 42
        // when
        kubeReplicas.set(replicas, replicator)
        // then
        verify(replicator).replicas = replicas
    }

    @Test
    fun `#set should call operator#replace(replicator#resource)`() {
        // given
        val deployment = mock<Deployment>()
        val replicator: Replicator = mock {
            on (mock.resource) doReturn deployment
        }
        // when
        kubeReplicas.set(42, replicator)
        // then
        verify(operator).replace(deployment)
    }

    private fun createDeploymentSpec(matchLabels: Map<String, String>): DeploymentSpec {
        val selector: LabelSelector = LabelSelectorBuilder()
            .withMatchLabels<String, String>(matchLabels)
            .build()
        val spec: DeploymentSpec = mock {
            on(mock.selector) doReturn selector
        }
        return spec
    }

    private fun createReplicationControllerSpec(selector: Map<String, String>): ReplicationControllerSpec {
        return mock {
            whenever(mock.selector) doReturn selector
        }
    }

    private fun createReplicaSetSpec(matchLabels: Map<String, String>): ReplicaSetSpec {
        val selector: LabelSelector = LabelSelectorBuilder()
            .withMatchLabels<String, String>(matchLabels)
            .build()
        return mock {
            whenever(mock.selector) doReturn selector
        }
    }

    private fun createStatefulSetSpec(matchLabels: Map<String, String>): StatefulSetSpec {
        val selector: LabelSelector = LabelSelectorBuilder()
            .withMatchLabels<String, String>(matchLabels)
            .build()
        return mock {
            whenever(mock.selector) doReturn selector
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