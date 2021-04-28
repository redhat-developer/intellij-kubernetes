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
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import io.fabric8.kubernetes.client.KubernetesClient

open class KubernetesContext(
	modelChange: IModelChangeObservable,
	clients: Clients<KubernetesClient>,
	context: NamedContext
) : ActiveContext<Namespace, KubernetesClient>(modelChange, clients, context) {

	override fun getInternalResourceOperators(clients: Clients<KubernetesClient>)
			: List<IResourceOperator<out HasMetadata>> {
		return OperatorFactory.createKubernetes(clients)
	}

	override fun getNamespacesKind(): ResourceKind<Namespace> {
		return NamespacesOperator.KIND
	}

	override fun isOpenShift() = false
}
