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
import com.redhat.devtools.intellij.kubernetes.model.client.OSClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.OperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.OpenShiftClient

open class OpenShiftContext(
    context: NamedContext,
    modelChange: IResourceModelObservable,
    client: OSClientAdapter,
) : ActiveContext<Project, OpenShiftClient>(context, modelChange, client) {

	override val namespaceKind =  ProjectsOperator.KIND

	override fun getInternalResourceOperators(client: ClientAdapter<out OpenShiftClient>): List<IResourceOperator<out HasMetadata>> {
		return OperatorFactory.createOpenShift(client)
	}

	override fun isOpenShift() = true
}
