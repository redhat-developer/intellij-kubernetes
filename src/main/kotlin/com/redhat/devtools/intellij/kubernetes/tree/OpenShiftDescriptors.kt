/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.context.OpenShiftContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import javax.swing.Icon

object OpenShiftDescriptors {

    fun createDescriptor(
        element: Any,
        childrenKind: ResourceKind<out HasMetadata>?,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ): NodeDescriptor<*>? {
        return when (element) {
            is OpenShiftContext -> OpenShiftContextDescriptor(element, model, project)
            is io.fabric8.openshift.api.model.Project -> ProjectDescriptor(element, parent, model, project)
            is ImageStream,
            is DeploymentConfig,
            is ReplicationController,
            is BuildConfig,
            is Build -> TreeStructure.ResourceDescriptor(element as HasMetadata, childrenKind, parent, model, project)
            else -> null
        }
    }

    private class OpenShiftContextDescriptor(
        context: OpenShiftContext,
        model: IResourceModel,
        project: Project
    ) : TreeStructure.ContextDescriptor<OpenShiftContext>(
        context = context,
        model = model,
        project = project
    ) {
        override fun getIcon(element: OpenShiftContext): Icon {
            return IconLoader.getIcon("/icons/openshift-cluster.svg", javaClass)
        }
    }

    private class ProjectDescriptor(
        element: io.fabric8.openshift.api.model.Project,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : TreeStructure.ResourceDescriptor<io.fabric8.openshift.api.model.Project>(
        element,
        null,
        parent,
        model,
        project
    ) {

        override fun getLabel(element: io.fabric8.openshift.api.model.Project): String {
            var label = element.metadata.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label
        }
    }
}