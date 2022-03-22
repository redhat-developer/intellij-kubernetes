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
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.LeafState
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.resource.BuildFor
import com.redhat.devtools.intellij.kubernetes.model.resource.ReplicationControllerFor
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ImageStreamsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import com.redhat.devtools.intellij.kubernetes.model.resourceName
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Project

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = Folder("Projects", ProjectsOperator.KIND)
        val IMAGESTREAMS = Folder("ImageStreams", ImageStreamsOperator.KIND)
        val DEPLOYMENTCONFIGS = Folder("DeploymentConfigs", DeploymentConfigsOperator.KIND)
        val BUILDCONFIGS = Folder("BuildConfigs", BuildConfigsOperator.KIND)
    }

    override val elementsTree: List<ElementNode<*>> = listOf(
        *createProjectsElements(),
        *createWorkloadElements()
    )

    private fun createProjectsElements(): Array<ElementNode<*>> {
        return arrayOf(
            element<Any> {
                applicableIf { it == getRootElement() }
                children {
                    listOf(
                        PROJECTS
                    )
                }
            },
            element<Any> {
                applicableIf { it == PROJECTS }
                childrenKind { ProjectsOperator.KIND }
                children {
                    model.resources(ProjectsOperator.KIND)
                        .inNoNamespace()
                        .list()
                        .sortedBy(resourceName)
                }
            }
        )
    }

    private fun createWorkloadElements(): Array<ElementNode<*>> {
        return arrayOf(
            element<Any> {
                applicableIf { it == WORKLOADS }
                children {
                    listOf<Any>(
                        IMAGESTREAMS,
                        DEPLOYMENTCONFIGS,
                        BUILDCONFIGS
                    )
                }
            },
            element<Any> {
                applicableIf { it == IMAGESTREAMS }
                childrenKind { ImageStreamsOperator.KIND }
                children {
                    model.resources(ImageStreamsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<Any> {
                applicableIf { it == DEPLOYMENTCONFIGS }
                childrenKind { DeploymentConfigsOperator.KIND }
                children {
                    model.resources(DeploymentConfigsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<DeploymentConfig> {
                applicableIf { it is DeploymentConfig }
                childrenKind { ReplicationControllersOperator.KIND }
                children {
                    model.resources(ReplicationControllersOperator.KIND)
                        .inCurrentNamespace()
                        .filtered(ReplicationControllerFor(it))
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<Any> {
                applicableIf { it == BUILDCONFIGS }
                childrenKind { BuildConfigsOperator.KIND }
                children {
                    model.resources(BuildConfigsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<BuildConfig> {
                applicableIf { it is BuildConfig }
                childrenKind { BuildsOperator.KIND }
                children {
                    model.resources(BuildsOperator.KIND)
                        .inCurrentNamespace()
                        .filtered(BuildFor(it))
                        .list()
                        .sortedBy(resourceName)
                }
            }
        )
    }

    override fun descriptorFactory(): (Any, ResourceKind<out HasMetadata>?, NodeDescriptor<*>?, IResourceModel, com.intellij.openapi.project.Project) -> NodeDescriptor<*>? {
        return OpenShiftDescriptors::createDescriptor
    }

    override fun canContribute(): Boolean {
        return model.getCurrentContext()?.isOpenShift() ?: false
    }

    override fun getLeafState(element: Any): LeafState? {
        return when(element) {
            is Project -> LeafState.ALWAYS
            else -> null
        }
    }

}