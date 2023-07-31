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
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.Replicator
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec
import io.fabric8.kubernetes.api.model.apps.*
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReplicatorTest {

    @Test
    fun `#replicas should return Deployment replicas`() {
        // given
        val spec: DeploymentSpec = mock()
        val deployment = DeploymentBuilder()
            .withSpec(spec)
            .build()
        val replicator = Replicator(deployment)
        // when
        replicator.replicas
        // then
        verify(spec).replicas
    }

    @Test
    fun `#replicas should return ReplicationController replicas`() {
        // given
        val spec: ReplicationControllerSpec = mock()
        val replicationController = ReplicationControllerBuilder()
            .withSpec(spec)
            .build()
        val replicator = Replicator(replicationController)
        // when
        replicator.replicas
        // then
        verify(spec).replicas
    }

    @Test
    fun `#replicas should return ReplicaSet replicas`() {
        // given
        val spec: ReplicaSetSpec = mock()
        val replicaSet = ReplicaSetBuilder()
            .withSpec(spec)
            .build()
        val replicator = Replicator(replicaSet)
        // when
        replicator.replicas
        // then
        verify(spec).replicas
    }

    @Test
    fun `#replicas should return StatefulSet replicas`() {
        // given
        val spec: StatefulSetSpec = mock()
        val replicaSet = StatefulSetBuilder()
            .withSpec(spec)
            .build()
        val replicator = Replicator(replicaSet)
        // when
        replicator.replicas
        // then
        verify(spec).replicas
    }

    @Test
    fun `#replicas should return null for Job replicas`() {
        // given
        val job = JobBuilder().build()
        val replicator = Replicator(job)
        // when
        val replicas = replicator.replicas
        // then
        assertThat(replicas).isNull()
    }

    @Test
    fun `#replicas = XX should set Deployment replicas`() {
        // given
        val spec: DeploymentSpec = mock()
        val deployment: Deployment = mock {
            on { this.spec } doReturn spec
        }
        val replicator = Replicator(deployment)
        val replicas = 42
        // when
        replicator.replicas = replicas
        // then
        verify(spec).replicas = replicas
    }

    @Test
    fun `#replicas = XX should set ReplicationController replicas`() {
        // given
        val spec: ReplicationControllerSpec = mock()
        val replicationController: ReplicationController = mock {
            on { this.spec } doReturn spec
        }
        val replicator = Replicator(replicationController)
        val replicas = 84
        // when
        replicator.replicas = replicas
        // then
        verify(spec).replicas = replicas
    }

    @Test
    fun `#replicas = XX should set ReplicaSet replicas`() {
        // given
        val spec: ReplicaSetSpec = mock()
        val replicaSet: ReplicaSet = mock {
            on { this.spec } doReturn spec
        }
        val replicator = Replicator(replicaSet)
        val replicas = 22
        // when
        replicator.replicas = replicas
        // then
        verify(spec).replicas = replicas
    }

    @Test
    fun `#replicas = XX should set StatefulSet replicas`() {
        // given
        val spec: StatefulSetSpec = mock()
        val statefulSet: StatefulSet = mock {
            on { this.spec } doReturn spec
        }
        val replicator = Replicator(statefulSet)
        val replicas = 42
        // when
        replicator.replicas = replicas
        // then
        verify(spec).replicas = replicas
    }

}