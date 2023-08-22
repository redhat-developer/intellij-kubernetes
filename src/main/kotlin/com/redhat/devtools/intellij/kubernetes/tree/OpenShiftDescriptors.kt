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
import com.redhat.devtools.intellij.kubernetes.tree.OpenShiftStructure.ProjectsFolder
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ContextDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.FolderDescriptor
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Route
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

            is ProjectsFolder -> ProjectsFolderDescriptor(element, parent, model, project)

            is io.fabric8.openshift.api.model.Project -> ProjectDescriptor(element, parent, model, project)
            is ImageStream,
            is DeploymentConfig,
            is ReplicationController,
            is BuildConfig,
            is Build,
            is Route -> ResourceDescriptor(element as HasMetadata, childrenKind, parent, model, project)
            else -> null
        }
    }

    private class OpenShiftContextDescriptor(
        context: OpenShiftContext,
        model: IResourceModel,
        project: Project
    ) : ContextDescriptor<OpenShiftContext>(
        context = context,
        model = model,
        project = project
    ) {
        override fun getIcon(element: OpenShiftContext): Icon {
            return IconLoader.getIcon("/icons/openshift-cluster.svg", javaClass)
        }
    }

    private class ProjectsFolderDescriptor(
        element: ProjectsFolder,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : FolderDescriptor(
        element,
        parent,
        model,
        project
    ) {
        override fun getSubLabel(element: Folder): String {
            val current = model.getCurrentNamespace()
            return "current: ${
                if (current.isNullOrEmpty()) {
                    "<none>"
                } else {
                    current
                }
            }"
        }
    }

    private class ProjectDescriptor(
        element: io.fabric8.openshift.api.model.Project,
        parent: NodeDescriptor<*>?,
        model: IResourceModel,
        project: Project
    ) : ResourceDescriptor<io.fabric8.openshift.api.model.Project>(
        element,
        null,
        parent,
        model,
        project
    ) {

        override fun getLabel(element: io.fabric8.openshift.api.model.Project?): String {
            var label = element?.metadata?.name
            if (label == model.getCurrentNamespace()) {
                label = "* $label"
            }
            return label ?: "unknown"
        }
    }
}