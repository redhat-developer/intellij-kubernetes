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
package com.redhat.devtools.intellij.kubernetes.model.context

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
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

open class OpenShiftContext(
    modelChange: IModelChangeObservable,
    client: NamespacedOpenShiftClient,
	context: NamedContext
) : ActiveContext<Project, OpenShiftClient>(modelChange, client, context) {

	override fun getInternalResourceOperators(supplier: Clients<OpenShiftClient>)
			: List<IResourceOperator<out HasMetadata>> {
		val client = supplier.get(OpenShiftClient::class.java)
		return listOf(
				NamespacesOperator(client),
				NodesOperator(client),
				AllPodsOperator(client),
				DeploymentsOperator(supplier.getApps()),
				StatefulSetsOperator(supplier.getApps()),
				DaemonSetsOperator(supplier.getApps()),
				JobsOperator(supplier.getBatch()),
				CronJobsOperator(supplier.getBatch()),
				NamespacedPodsOperator(client),
				ProjectsOperator(client),
				ImageStreamsOperator(client),
				DeploymentConfigsOperator(client),
				BuildsOperator(client),
				BuildConfigsOperator(client),
				ReplicationControllersOperator(client),
				ServicesOperator(client),
				EndpointsOperator(client),
				PersistentVolumesOperator(client),
				PersistentVolumeClaimsOperator(client),
				StorageClassesOperator(supplier.getStorage()),
				ConfigMapsOperator(client),
				SecretsOperator(client),
				IngressOperator(supplier.getExtensions()),
				CustomResourceDefinitionsOperator(client)
		)
	}

	override fun getNamespacesKind(): ResourceKind<Project> {
		return ProjectsOperator.KIND
	}

	override fun isOpenShift() = true
}
