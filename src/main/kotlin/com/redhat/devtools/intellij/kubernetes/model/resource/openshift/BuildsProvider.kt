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
package com.redhat.devtools.intellij.kubernetes.model.resource.openshift

import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceOperation
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.client.OpenShiftClient
import java.util.function.Supplier

class BuildsProvider(client: OpenShiftClient)
    : NamespacedResourcesProvider<Build, OpenShiftClient>(client) {

    companion object {
        val KIND = ResourceKind.create(Build::class.java)
    }

    override val kind = KIND

    override fun getNamespacedOperation(namespace: String): Supplier<ResourceOperation<Build>?> {
        return Supplier { client.builds().inNamespace(namespace) }
    }

    override fun getNonNamespacedOperation(): Supplier<ResourceOperation<Build>?> {
        return Supplier { client.builds().inAnyNamespace() as ResourceOperation<Build> }
    }
}
