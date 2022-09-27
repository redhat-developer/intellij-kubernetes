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
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ConfigMapsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.CronJobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DaemonSetsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DeploymentsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.EndpointsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.IngressOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.JobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumeClaimsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.SecretsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ServicesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.StatefulSetsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.StorageClassesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ImageStreamsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.OpenShiftClient

object OperatorFactory {

    private val openshift =
        listOf<Pair<ResourceKind<out HasMetadata>, (ClientAdapter<out OpenShiftClient>) -> IResourceOperator<out HasMetadata>>>(
            NamespacesOperator.KIND to ::NamespacesOperator,
            NodesOperator.KIND to ::NodesOperator,
            AllPodsOperator.KIND to ::AllPodsOperator,
            DeploymentsOperator.KIND to ::DeploymentsOperator,
            StatefulSetsOperator.KIND to ::StatefulSetsOperator,
            DaemonSetsOperator.KIND to ::DaemonSetsOperator,
            JobsOperator.KIND to ::JobsOperator,
            CronJobsOperator.KIND to ::CronJobsOperator,
            NamespacedPodsOperator.KIND to ::NamespacedPodsOperator,
            ProjectsOperator.KIND to ::ProjectsOperator,
            ImageStreamsOperator.KIND to ::ImageStreamsOperator,
            DeploymentConfigsOperator.KIND to ::DeploymentConfigsOperator,
            BuildsOperator.KIND to ::BuildsOperator,
            BuildConfigsOperator.KIND to ::BuildConfigsOperator,
            ReplicationControllersOperator.KIND to ::ReplicationControllersOperator,
            ServicesOperator.KIND to ::ServicesOperator,
            EndpointsOperator.KIND to ::EndpointsOperator,
            PersistentVolumesOperator.KIND to ::PersistentVolumesOperator,
            PersistentVolumeClaimsOperator.KIND to ::PersistentVolumeClaimsOperator,
            StorageClassesOperator.KIND to ::StorageClassesOperator,
            ConfigMapsOperator.KIND to ::ConfigMapsOperator,
            SecretsOperator.KIND to ::SecretsOperator,
            IngressOperator.KIND to ::IngressOperator,
            CustomResourceDefinitionsOperator.KIND to ::CustomResourceDefinitionsOperator
        )

    private val kubernetes =
        listOf<Pair<ResourceKind<out HasMetadata>, (ClientAdapter<out KubernetesClient>) -> IResourceOperator<out HasMetadata>>>(
            NamespacesOperator.KIND to ::NamespacesOperator,
            NodesOperator.KIND to ::NodesOperator,
            AllPodsOperator.KIND to ::AllPodsOperator,
            DeploymentsOperator.KIND to ::DeploymentsOperator,
            StatefulSetsOperator.KIND to ::StatefulSetsOperator,
            DaemonSetsOperator.KIND to ::DaemonSetsOperator,
            JobsOperator.KIND to ::JobsOperator,
            CronJobsOperator.KIND to ::CronJobsOperator,
            NamespacedPodsOperator.KIND to ::NamespacedPodsOperator,
            ServicesOperator.KIND to ::ServicesOperator,
            EndpointsOperator.KIND to ::EndpointsOperator,
            PersistentVolumesOperator.KIND to ::PersistentVolumesOperator,
            PersistentVolumeClaimsOperator.KIND to ::PersistentVolumeClaimsOperator,
            StorageClassesOperator.KIND to ::StorageClassesOperator,
            ConfigMapsOperator.KIND to ::ConfigMapsOperator,
            SecretsOperator.KIND to ::SecretsOperator,
            IngressOperator.KIND to ::IngressOperator,
            CustomResourceDefinitionsOperator.KIND to ::CustomResourceDefinitionsOperator
        )

    fun createKubernetes(client: ClientAdapter<out KubernetesClient>): List<IResourceOperator<out HasMetadata>> {
        return kubernetes.map { it.second.invoke(client) }
    }

    fun createOpenShift(client: ClientAdapter<out OpenShiftClient>): List<IResourceOperator<out HasMetadata>> {
        return openshift.map { it.second.invoke(client) }
    }

    fun createAll(client: ClientAdapter<out KubernetesClient>): List<IResourceOperator<out HasMetadata>>{
        return if (client.isOpenShift()) {
            @Suppress("UNCHECKED_CAST")
            createOpenShift(client as ClientAdapter<OpenShiftClient>)
        } else {
            createKubernetes(client)
        }
    }

    inline fun <reified T : IResourceOperator<out HasMetadata>> create(
        kind: ResourceKind<out HasMetadata>,
        client: ClientAdapter<out KubernetesClient>
    ): T? {
        val operators = getOperatorsByKind(client)
        return operators
            .filter { kindAndOperator -> kind == kindAndOperator.first }
            .map { kindAndOperator -> kindAndOperator.second.invoke(client) }
            .filterIsInstance<T>()
            .firstOrNull()
    }

    fun getOperatorsByKind(
        client: ClientAdapter<out KubernetesClient>
    ): List<Pair<ResourceKind<out HasMetadata>, (ClientAdapter<out KubernetesClient>) -> IResourceOperator<out HasMetadata>>> {
        return if (client.isOpenShift()) {
            @Suppress("UNCHECKED_CAST")
            openshift as List<Pair<ResourceKind<out HasMetadata>, (ClientAdapter<out KubernetesClient>) -> IResourceOperator<out HasMetadata>>>
        } else {
            kubernetes
        }
    }
}