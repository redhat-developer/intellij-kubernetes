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

import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.client.OpenShiftClient
import org.jboss.tools.intellij.kubernetes.model.resource.NamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.WatchableAndListable

class ImageStreamsProvider(client: OpenShiftClient)
    : NamespacedResourcesProvider<ImageStream, OpenShiftClient>(client) {

    companion object {
        val KIND = ResourceKind.new(ImageStream::class.java)
    }

    override val kind = KIND

    override fun getOperation(namespace: String): () -> WatchableAndListable<ImageStream>? {
        return { client.imageStreams().inNamespace(namespace) }
    }
}
