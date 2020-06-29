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
package org.jboss.tools.intellij.kubernetes.model.context

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.*
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.AllPodsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.ConfigMapsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.EndpointsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.IngressProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NodesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumeClaimsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.SecretsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.ServicesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.JobsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.StorageClassesProvider

open class KubernetesContext(
		modelChange: IModelChangeObservable,
		client: NamespacedKubernetesClient,
		context: NamedContext
) : ActiveContext<Namespace, NamespacedKubernetesClient>(modelChange, client, context) {

	override fun getInternalResourceProviders(client: NamespacedKubernetesClient)
			: List<IResourcesProvider<out HasMetadata>> {
		return listOf(
				NamespacesProvider(client),
				NodesProvider(client),
				AllPodsProvider(client),
				JobsProvider(client),
				NamespacedPodsProvider(client),
				ServicesProvider(client),
				EndpointsProvider(client),
				PersistentVolumesProvider(client),
				PersistentVolumeClaimsProvider(client),
				StorageClassesProvider(client),
				ConfigMapsProvider(client),
				SecretsProvider(client),
				IngressProvider(client)
		)
	}

	override fun getNamespaces(): Collection<Namespace> {
		return getResources(NamespacesProvider.KIND, ResourcesIn.NO_NAMESPACE)
	}

	override fun isOpenShift() = false
}
