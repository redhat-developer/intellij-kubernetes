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
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.discovery.Endpoint
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
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.CustomResourceDefinitionsOperator
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

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
        val NAMESPACES = Folder("Namespaces", NamespacesOperator.KIND)
        val NODES = Folder("Nodes", NodesOperator.KIND)
        val WORKLOADS = Folder("Workloads", null)
			val DEPLOYMENTS = Folder("Deployments", DeploymentsOperator.KIND) //  Workloads / Deployments
			val STATEFULSETS = Folder("StatefulSets", StatefulSetsOperator.KIND) //  Workloads / StatefulSets
			val DAEMONSETS = Folder("DaemonSets", DaemonSetsOperator.KIND) //  Workloads / Pods
			val JOBS = Folder("Jobs", JobsOperator.KIND) //  Workloads / StatefulSets
			val CRONJOBS = Folder("CronJobs", CronJobsOperator.KIND) //  Workloads / StatefulSets
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
				anchor { it == getRootElement() }
				childElements {
					listOf(NAMESPACES,
							NODES,
							WORKLOADS,
							NETWORK,
							STORAGE,
							CONFIGURATION,
							CUSTOM_RESOURCES_DEFINITIONS
					)
				}
				parentElements { model }
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
		val node = elementsTree.find { it.isAnchor(element) }
		return node?.getChildElements(element) ?: emptyList()
	}

	override fun getParentElement(element: Any): Any? {
		try {
			// default to null to allow tree structure to choose default parent element
			val node = elementsTree.find { it.isAnchor(element) } ?: return null
			return node.getParentElements(element)
		} catch (e: ResourceException) {
			return null
		}
	}

	override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?, project: Project): NodeDescriptor<*>? {
		return KubernetesDescriptors.createDescriptor(element, parent, model, project)
	}

	override fun canContribute() = true

	private fun createNamespacesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NAMESPACES }
					childElements {
						model.resources(NamespacesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it is Namespace }
					parentElements { NAMESPACES }
				}
		)
	}

	private fun createPodElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Pod> {
					anchor { it is Pod }
					childElements {
						KubernetesDescriptors.createPodDescriptorFactories(it)
					}
					parentElements {
						listOf(PODS,
								NODES,
								SERVICES,
								STATEFULSETS,
								DAEMONSETS)
					}
				}
		)
	}

	private fun createNodesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NODES }
					childElements {
						model.resources(NodesOperator.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it is Node }
					childElements {
						model.resources(AllPodsOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NODES }
				}
		)
	}

	private fun createWorkloadElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == WORKLOADS }
					childElements {
						listOf<Any>(DEPLOYMENTS,
								STATEFULSETS,
								DAEMONSETS,
								JOBS,
								CRONJOBS,
								PODS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == DEPLOYMENTS }
					childElements {
						model.resources(DeploymentsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Deployment> {
					anchor { it is Deployment }
					childElements {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDeployment(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { DEPLOYMENTS }
				},
				element<Any> {
					anchor { it == STATEFULSETS }
					childElements {
						model.resources(StatefulSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<StatefulSet> {
					anchor { it is StatefulSet }
					childElements {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForStatefulSet(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == DAEMONSETS }
					childElements {
						model.resources(DaemonSetsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<DaemonSet> {
					anchor { it is DaemonSet }
					childElements {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForDaemonSet(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == JOBS }
					childElements {
						model.resources(JobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == CRONJOBS }
					childElements {
						model.resources(CronJobsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == PODS }
					childElements {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				}
		)
	}

	private fun createNetworkElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NETWORK }
					childElements {
						listOf<Any>(
								SERVICES,
								ENDPOINTS,
								INGRESS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == SERVICES }
					childElements {
						model.resources(ServicesOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Service> {
					anchor { it is Service }
					childElements {
						model.resources(NamespacedPodsOperator.KIND)
								.inCurrentNamespace()
								.filtered(PodForService(it))
								.list()
								.sortedBy(resourceName)
					}
					parentElements { SERVICES }
				},
				element<Any> {
					anchor { it == ENDPOINTS }
					childElements {
						model.resources(EndpointsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Any> {
					anchor { it == INGRESS }
					childElements {
						model.resources(IngressOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				}
		)
	}

	private fun createStorageElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == STORAGE }
					childElements {
						listOf<Any>(
								PERSISTENT_VOLUMES,
								PERSISTENT_VOLUME_CLAIMS,
								STORAGE_CLASSES)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == PERSISTENT_VOLUMES }
					childElements {
						model.resources(PersistentVolumesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == PERSISTENT_VOLUME_CLAIMS }
					childElements {
						model.resources(PersistentVolumeClaimsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == STORAGE_CLASSES }
					childElements {
						model.resources(StorageClassesOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				}
		)
	}

	private fun createConfigurationElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == CONFIGURATION }
					childElements {
						listOf<Any>(
								CONFIG_MAPS,
								SECRETS)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it == CONFIG_MAPS }
					childElements {
						model.resources(ConfigMapsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<ConfigMap> {
					anchor { it is ConfigMap }
					childElements {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
					parentElements { CONFIGURATION }
				},
				element<Any> {
					anchor { it == SECRETS }
					childElements {
						model.resources(SecretsOperator.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<Secret>{
					anchor { it is Secret }
					childElements {
						KubernetesDescriptors.createDataDescriptorFactories((it).data, it)
					}
					parentElements { CONFIGURATION }
				}
		)
	}

	private fun createCustomResourcesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == CUSTOM_RESOURCES_DEFINITIONS }
					childElements {
						model.resources(CustomResourceDefinitionsOperator.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<CustomResourceDefinition> {
					anchor { it is CustomResourceDefinition }
					childElements {
						model.resources(it)
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CUSTOM_RESOURCES_DEFINITIONS }
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
