/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient

object KubernetesResourcesModel {

    val kubeClient: NamespacedKubernetesClient = DefaultKubernetesClient(ConfigBuilder().build())
    private var namespaces: MutableCollection<Namespace> = mutableListOf()

    fun getNamespaces(): List<Namespace> {
        if (namespaces.isEmpty()) {
            namespaces.addAll(kubeClient.namespaces().list().items)
        }
        return namespaces.toList();
    }

    fun refresh() {

    }
}