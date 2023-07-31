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
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PodForReplicationController
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildFor
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.BuildsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.DeploymentConfigsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ImageStreamsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ProjectsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllerFor
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.ReplicationControllersOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.openshift.RoutesOperator
import com.redhat.devtools.intellij.kubernetes.model.resourceName
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.BuildConfig
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Project

class OpenShiftStructure(model: IResourceModel): AbstractTreeStructureContribution(model) {

    companion object Folders {
        val PROJECTS = ProjectsFolder("Projects")
        val IMAGESTREAMS = Folder("ImageStreams", kind = ImageStreamsOperator.KIND)
        val DEPLOYMENTCONFIGS = Folder("DeploymentConfigs", kind = DeploymentConfigsOperator.KIND)
        val BUILDCONFIGS = Folder("BuildConfigs", kind = BuildConfigsOperator.KIND)
        val ROUTES = Folder("Routes", kind = RoutesOperator.KIND)
    }

    override val elementsTree: List<ElementNode<*>> = listOf(
        *createProjectsElements(),
        *createWorkloadElements(),
        *createNetworkElements()
    )

    private fun createProjectsElements(): Array<ElementNode<*>> {
        return arrayOf(
            element<IActiveContext<*,*>> {
                applicableIf { it == getRootElement() }
                children {
                    listOf(
                        PROJECTS
                    )
                }
            },
            element<Folder> {
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
            element<Folder> {
                applicableIf { it == WORKLOADS }
                children {
                    listOf(
                        IMAGESTREAMS,
                        DEPLOYMENTCONFIGS,
                        BUILDCONFIGS
                    )
                }
            },
            element<Folder> {
                applicableIf { it == IMAGESTREAMS }
                childrenKind { ImageStreamsOperator.KIND }
                children {
                    model.resources(ImageStreamsOperator.KIND)
                        .inCurrentNamespace()
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<Folder> {
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
            element<ReplicationController> {
                applicableIf { it is ReplicationController }
                childrenKind { NamespacedPodsOperator.KIND }
                children {
                    model.resources(NamespacedPodsOperator.KIND)
                        .inCurrentNamespace()
                        .filtered(PodForReplicationController(it))
                        .list()
                        .sortedBy(resourceName)
                }
            },
            element<Folder> {
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

    private fun createNetworkElements(): Array<ElementNode<*>> {
        return arrayOf(
            element<Folder> {
                applicableIf { it == NETWORK }
                children {
                    listOf(
                        ROUTES
                    )
                }
            },
            element<Folder> {
                applicableIf { it == ROUTES }
                childrenKind { RoutesOperator.KIND }
                children {
                    model.resources(RoutesOperator.KIND)
                        .inCurrentNamespace()
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

    class ProjectsFolder(label: String): Folder(label, ProjectsOperator.KIND)

}