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
import io.fabric8.openshift.client.OpenShiftClient
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.util.Clients

open class OpenShiftContext(
    modelChange: IModelChangeObservable,
    clients: Clients<OpenShiftClient>,
	context: NamedContext
) : ActiveContext<Project, OpenShiftClient>(modelChange, clients, context) {

	override fun getInternalResourceOperators(clients: Clients<OpenShiftClient>)
			: List<IResourceOperator<out HasMetadata>> {
		return OperatorFactory.createOpenShift(clients)
	}

	override fun getNamespacesKind(): ResourceKind<Project> {
		return ProjectsOperator.KIND
	}

	override fun isOpenShift() = true
}
