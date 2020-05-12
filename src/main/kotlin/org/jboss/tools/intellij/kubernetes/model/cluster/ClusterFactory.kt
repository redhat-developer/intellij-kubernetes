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
package org.jboss.tools.intellij.kubernetes.model.cluster

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.client.NamespacedOpenShiftClient
import io.fabric8.openshift.client.OpenShiftNotAvailableException
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import java.lang.RuntimeException

class ClusterFactory {

    fun create(observable: IModelChangeObservable): IActiveCluster<out HasMetadata, out KubernetesClient> {
        val k8Client = DefaultKubernetesClient()
        try {
            val osClient = k8Client.adapt(NamespacedOpenShiftClient::class.java)
            return OpenShiftCluster(
                observable,
                osClient
            )
        } catch(e: RuntimeException) {
            when(e) {
                is KubernetesClientException,
                is OpenShiftNotAvailableException ->
                    return KubernetesCluster(
                        observable,
                        k8Client)
                else -> throw e
            }
        }
    }

}