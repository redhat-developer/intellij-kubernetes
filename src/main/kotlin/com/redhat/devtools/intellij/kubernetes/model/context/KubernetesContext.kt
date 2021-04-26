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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
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
import com.redhat.devtools.intellij.kubernetes.model.util.Clients

open class KubernetesContext(
	modelChange: IModelChangeObservable,
	client: NamespacedKubernetesClient,
	context: NamedContext
) : ActiveContext<Namespace, NamespacedKubernetesClient>(modelChange, client, context) {

	override fun getInternalResourceOperators(clients: Clients<NamespacedKubernetesClient>)
			: List<IResourceOperator<out HasMetadata>> {
		val client = clients.get()
		return listOf(
				NamespacesOperator(client),
				NodesOperator(client),
				AllPodsOperator(client),
				DeploymentsOperator(clients.getApps()),
				StatefulSetsOperator(clients.getApps()),
				DaemonSetsOperator(clients.getApps()),
				JobsOperator(clients.getBatch()),
				CronJobsOperator(clients.getBatch()),
				NamespacedPodsOperator(client),
				ServicesOperator(client),
				EndpointsOperator(client),
				PersistentVolumesOperator(client),
				PersistentVolumeClaimsOperator(client),
				StorageClassesOperator(clients.getStorage()),
				ConfigMapsOperator(client),
				SecretsOperator(client),
				IngressOperator(clients.getExtensions()),
				CustomResourceDefinitionsOperator(client)
		)
	}

	override fun getNamespacesKind(): ResourceKind<Namespace> {
		return NamespacesOperator.KIND
	}

	override fun isOpenShift() = false
}
