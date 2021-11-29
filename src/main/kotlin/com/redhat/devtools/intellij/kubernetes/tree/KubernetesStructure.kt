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
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.LeafState
import com.redhat.devtools.intellij.kubernetes.actions.getElement
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.discovery.v1beta1.Endpoint
import io.fabric8.kubernetes.api.model.storage.StorageClass
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.resource.PodForDaemonSet
import com.redhat.devtools.intellij.kubernetes.model.resource.PodForDeployment
import com.redhat.devtools.intellij.kubernetes.model.resource.PodForService
import com.redhat.devtools.intellij.kubernetes.model.resource.PodForStatefulSet
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ConfigMapsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.CronJobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DaemonSetsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.DeploymentsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.EndpointsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.IngressOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.JobsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumeClaimsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.SecretsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ServicesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.StatefulSetsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.StorageClassesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.resourceName
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIGURATION
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIG_MAPS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CRONJOBS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.CUSTOM_RESOURCES_DEFINITIONS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.DAEMONSETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.DEPLOYMENTS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.INGRESS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.JOBS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUMES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUME_CLAIMS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.SECRETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STATEFULSETS
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE_CLASSES
import com.redhat.devtools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Folder
import com.redhat.devtools.intellij.kubernetes.tree.util.getResourceKind

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
        val NAMESPACES = Folder("Namespaces", NamespacesOperator.KIND)
        val NODES = Folder("Nodes", NodesOperator.KIND)
        val WORKLOADS = Folder("Workloads", null)
			val DEPLOYMENTS = Folder("Deployments", DeploymentsOperator.KIND) //  Workloads / Deployments
			val STATEFULSETS = Folder("StatefulSets", StatefulSetsOperator.KIND) //  Workloads / StatefulSets
			val DAEMONSETS = Folder("DaemonSets", DaemonSetsOperator.KIND) //  Workloads / DaemonSets
			val JOBS = Folder("Jobs", JobsOperator.KIND) //  Workloads / Jobs
			val CRONJOBS = Folder("CronJobs", CronJobsOperator.KIND) //  Workloads / CronJobs
            val PODS = Folder("Pods", NamespacedPodsOperator.KIND) //  Workloads / Pods
        val NETWORK = Folder("Network", null)
            val SERVICES = Folder("Services", ServicesOperator.KIND) // Network / Services
            val ENDPOINTS = Folder("Endpoints", EndpointsOperator.KIND) // Network / Endpoints
			val INGRESS = Folder("Ingress", IngressOperator.KIND) // Network / Ingress
		val STORAGE = Folder("Storage", null)
			val PERSISTENT_VOLUMES = Folder("Persistent Volumes", PersistentVolumesOperator.KIND) // Storage / Persistent Volumes
			val PERSISTENT_VOLUME_CLAIMS = Folder("Persistent Volume Claims", PersistentVolumeClaimsOperator.KIND) // Storage / Persistent Volume Claims
			val STORAGE_CLASSES = Folder("Storage Classes", StorageClassesOperator.KIND) // Storage / Storage Classes
		val CONFIGURATION = Folder("Configuration", null)
			val CONFIG_MAPS = Folder("Config Maps", ConfigMapsOperator.KIND) // Configuration / Config Maps
			val SECRETS = Folder("Secrets", SecretsOperator.KIND) // Configuration / Secrets
		val CUSTOM_RESOURCES_DEFINITIONS = Folder("Custom Resources", CustomResourceDefinitionsOperator.KIND)
    }

	private val elementsTree: List<ElementNode<*>> = listOf(
			element<Any> {
				applicableIf { it == getRootElement() }
				children {
					listOf(
						NAMESPACES,
						NODES,
						WORKLOADS,
						NETWORK,
						STORAGE,
						CONFIGURATION,
						CUSTOM_RESOURCES_DEFINITIONS
					)
				}
			},
			*createPodElements(), // pods are in several places (NODES, WORKLOADS, NETWORK, etc)
			*createNamespacesElements(),
			*createNodesElements(),
			*createWorkloadElements(),
			*createNetworkElements(),
			*createStorageElements(),
			*createConfigurationElements(),
			*createCustomResourcesElements()
	)

	override fun getChildElements(element: Any): Collection<Any> {
		val node = elementsTree.find { it.isApplicableFor(element) } ?: return emptyList()
		return node.getChildElements(element)
	}

	override fun getParentElement(element: Any): Any? {
		return try {
			val kind = getResourceKind(element)
			return elementsTree.first { it.getChildrenKind() == kind }
		} catch (e: ResourceException) {
			// default to null to allow tree structure to choose default parent element
			null
		}
	}

	override fun isParentDescriptor(descriptor: NodeDescriptor<*>?, element: Any): Boolean {
		val kind = getResourceKind(element)
		val matchers: Collection<ElementNode<*>> = elementsTree.filter { it.getChildrenKind() == kind }
		return matchers.any { elementNode ->
			val descriptorElement = descriptor?.getElement<Any>() ?: return false
			elementNode.isApplicableFor(descriptorElement)
		}
	}

	override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?, project: Project): NodeDescriptor<*>? {
		val childrenKind = elementsTree.find { it.isApplicableFor(element) }?.getChildrenKind()
		return KubernetesDescriptors.createDescriptor(element, childrenKind, parent, model, project)
	}

	override fun canContribute() = true

	private fun createNamespacesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == NAMESPACES }
					children {
						model.resources(NamespacesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it is Namespace }
				}
		)
	}

	private fun createPodElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Pod> {
					applicableIf { it is Pod }
					children {
						KubernetesDescriptors.createPodDescriptorFactories(it)
					}
				}
		)
	}

	private fun createNodesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == NODES }
					childrenKind { NodesOperator.KIND }
					children {
						model.resources(NodesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it is Node }
					childrenKind { AllPodsOperator.KIND }
					children {
						model.resources(AllPodsOperator.KIND)
								.inAnyNamespace()
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
						listOf<Any>(DEPLOYMENTS,
								STATEFULSETS,
								DAEMONSETS,
								JOBS,
								CRONJOBS,
								PODS)
					}
				},
				element<Any> {
					applicableIf { it == DEPLOYMENTS }
					childrenKind { DeploymentsOperator.KIND }
					children {
						model.resources(DeploymentsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Deployment> {
					applicableIf { it is Deployment }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDeployment(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == STATEFULSETS }
					childrenKind { StatefulSetsOperator.KIND }
					children {
						model.resources(StatefulSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<StatefulSet> {
					applicableIf { it is StatefulSet }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForStatefulSet(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == DAEMONSETS }
					childrenKind { DaemonSetsOperator.KIND }
					children {
						model.resources(DaemonSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<DaemonSet> {
					applicableIf { it is DaemonSet }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDaemonSet(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == JOBS }
					childrenKind { JobsOperator.KIND }
					children {
						model.resources(JobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == CRONJOBS }
					childrenKind { CronJobsOperator.KIND }
					children {
						model.resources(CronJobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == PODS }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createNetworkElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == NETWORK }
					children {
						listOf<Any>(
								SERVICES,
								ENDPOINTS,
								INGRESS)
					}
				},
				element<Any> {
					applicableIf { it == SERVICES }
					childrenKind { ServicesOperator.KIND }
					children {
						model.resources(ServicesOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Service> {
					applicableIf { it is Service }
					childrenKind { NamespacedPodsOperator.KIND }
					children {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForService(it))
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == ENDPOINTS }
					childrenKind { EndpointsOperator.KIND }
					children {
						model.resources(EndpointsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == INGRESS }
					childrenKind { IngressOperator.KIND }
					children {
						model.resources(IngressOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createStorageElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == STORAGE }
					children {
						listOf<Any>(
								PERSISTENT_VOLUMES,
								PERSISTENT_VOLUME_CLAIMS,
								STORAGE_CLASSES)
					}
				},
				element<Any> {
					applicableIf { it == PERSISTENT_VOLUMES }
					children {
						model.resources(PersistentVolumesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == PERSISTENT_VOLUME_CLAIMS }
					childrenKind { PersistentVolumeClaimsOperator.KIND }
					children {
						model.resources(PersistentVolumeClaimsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Any> {
					applicableIf { it == STORAGE_CLASSES }
					childrenKind { StorageClassesOperator.KIND }
					children {
						model.resources(StorageClassesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	private fun createConfigurationElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == CONFIGURATION }
					children {
						listOf<Any>(
								CONFIG_MAPS,
								SECRETS)
					}
				},
				element<Any> {
					applicableIf { it == CONFIG_MAPS }
					childrenKind { ConfigMapsOperator.KIND }
					children {
						model.resources(ConfigMapsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<ConfigMap> {
					applicableIf { it is ConfigMap }
					children {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
				},
				element<Any> {
					applicableIf { it == SECRETS }
					childrenKind { SecretsOperator.KIND }
					children {
						model.resources(SecretsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<Secret>{
					applicableIf { it is Secret }
					children {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
				}
		)
	}

	private fun createCustomResourcesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					applicableIf { it == CUSTOM_RESOURCES_DEFINITIONS }
					childrenKind { CustomResourceDefinitionsOperator.KIND }
					children {
						model.resources(CustomResourceDefinitionsOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
				},
				element<CustomResourceDefinition> {
					applicableIf { it is CustomResourceDefinition }
					children {
						model.resources(it)
								.list()
								.sortedBy(resourceName)
					}
				}
		)
	}

	override fun getLeafState(element: Any): LeafState? {
		return when(element) {
			is Namespace,
			is Endpoint,
			is StorageClass,
			is GenericCustomResource -> LeafState.ALWAYS
			else -> null
		}
	}
}
