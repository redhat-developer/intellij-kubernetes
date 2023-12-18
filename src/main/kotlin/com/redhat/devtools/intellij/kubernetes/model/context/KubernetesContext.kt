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

import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.dashboard.KubernetesDashboard
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.NonCachingSingleResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.Replicator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.ResourcesRetrieval
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient

open class KubernetesContext(
	context: NamedContext,
	modelChange: IResourceModelObservable,
	client: KubeClientAdapter,
) : ActiveContext<Namespace, KubernetesClient>(
		context,
		modelChange,
		client,
		KubernetesDashboard(
			client.get(),
			context.name,
			client.get().masterUrl.toExternalForm()
		)
) {

	override val namespaceKind : ResourceKind<Namespace> =  NamespacesOperator.KIND

	private val replicasOperator = KubernetesReplicas(
		NonCachingSingleResourceOperator(client),
		object: ResourcesRetrieval {
			override fun <T : HasMetadata> getAll(kind: ResourceKind<T>, resourcesIn: ResourcesIn): Collection<T> {
				return getAllResources(kind, resourcesIn)
			}
		}
	)

	override fun getInternalResourceOperators(client: ClientAdapter<out KubernetesClient>)
			: List<IResourceOperator<out HasMetadata>> {
		return OperatorFactory.createKubernetes(client)
	}

	override fun isOpenShift() = false

	override fun setReplicas(replicas: Int, replicator: Replicator) {
		replicasOperator.set(replicas, replicator)
	}

	override fun getReplicas(resource: HasMetadata): Replicator? {
		return replicasOperator.get(resource)
	}

	override fun getDashboardUrl(): String {
		return dashboard.get()
	}


}
