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

import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ConfigMapsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.CronJobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.CustomResourceDefinitionsOperator
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
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ImageStreamsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.OpenShiftClient

object OperatorFactory {

    fun createKubernetes(clients: Clients<out KubernetesClient>): List<IResourceOperator<out HasMetadata>> {
        return listOf(
            NamespacesOperator(clients.get()),
            NodesOperator(clients.get()),
            AllPodsOperator(clients.get()),
            DeploymentsOperator(clients.getApps()),
            StatefulSetsOperator(clients.getApps()),
            DaemonSetsOperator(clients.getApps()),
            JobsOperator(clients.getBatch()),
            CronJobsOperator(clients.getBatch()),
            NamespacedPodsOperator(clients.get()),
            ServicesOperator(clients.get()),
            EndpointsOperator(clients.get()),
            PersistentVolumesOperator(clients.get()),
            PersistentVolumeClaimsOperator(clients.get()),
            StorageClassesOperator(clients.getStorage()),
            ConfigMapsOperator(clients.get()),
            SecretsOperator(clients.get()),
            IngressOperator(clients.getExtensions()),
            CustomResourceDefinitionsOperator(clients.get())
        )
    }

    fun createOpenShift(clients: Clients<out OpenShiftClient>): List<IResourceOperator<out HasMetadata>> {
        return listOf(
            NamespacesOperator(clients.get()),
            NodesOperator(clients.get()),
            AllPodsOperator(clients.get()),
            DeploymentsOperator(clients.getApps()),
            StatefulSetsOperator(clients.getApps()),
            DaemonSetsOperator(clients.getApps()),
            JobsOperator(clients.getBatch()),
            CronJobsOperator(clients.getBatch()),
            NamespacedPodsOperator(clients.get()),
            ProjectsOperator(clients.get()),
            ImageStreamsOperator(clients.get()),
            DeploymentConfigsOperator(clients.get()),
            BuildsOperator(clients.get()),
            BuildConfigsOperator(clients.get()),
            ReplicationControllersOperator(clients.get()),
            ServicesOperator(clients.get()),
            EndpointsOperator(clients.get()),
            PersistentVolumesOperator(clients.get()),
            PersistentVolumeClaimsOperator(clients.get()),
            StorageClassesOperator(clients.getStorage()),
            ConfigMapsOperator(clients.get()),
            SecretsOperator(clients.get()),
            IngressOperator(clients.getExtensions()),
            CustomResourceDefinitionsOperator(clients.get())
        )
    }

    fun createAll(clients: Clients<out KubernetesClient>): List<IResourceOperator<out HasMetadata>>{
        val operators = mutableListOf<IResourceOperator<out HasMetadata>>()
        operators.addAll(createKubernetes(clients))
        if (clients.isOpenShift()) {
            @Suppress("UNCHECKED_CAST")
            operators.addAll(createOpenShift(clients as Clients<OpenShiftClient>))
        }
        return operators
    }
}