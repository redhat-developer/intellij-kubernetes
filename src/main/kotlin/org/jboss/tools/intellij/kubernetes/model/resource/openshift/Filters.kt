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
package org.jboss.tools.intellij.kubernetes.model.resource.openshift

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import java.util.function.Predicate

class ReplicationControllerFor(private val dc: DeploymentConfig) : Predicate<ReplicationController> {

	private val deploymentConfigAnnotation = "openshift.io/deployment-config.name"

	override fun test(rc: ReplicationController): Boolean {
		return dc.metadata.name == rc.metadata.annotations[deploymentConfigAnnotation]
	}
}

class DeploymentConfigFor(private val dc: ReplicationController) : Predicate<DeploymentConfig> {

	private val deploymentConfigAnnotation = "openshift.io/deployment-config.name"
	private val dcName: String? = dc.metadata.annotations[deploymentConfigAnnotation]

	override fun test(dc: DeploymentConfig): Boolean {
		return dcName != null
				&& dcName == dc.metadata.annotations[deploymentConfigAnnotation]
	}
}
