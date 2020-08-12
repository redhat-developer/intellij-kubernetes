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
package org.jboss.tools.intellij.kubernetes.tree

import com.intellij.ide.util.treeView.NodeDescriptor
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.ResourceException
import org.jboss.tools.intellij.kubernetes.model.resource.PodForDaemonSet
import org.jboss.tools.intellij.kubernetes.model.resource.PodForDeployment
import org.jboss.tools.intellij.kubernetes.model.resource.PodForService
import org.jboss.tools.intellij.kubernetes.model.resource.PodForStatefulSet
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.AllPodsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.ConfigMapsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.CronJobsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.CustomResourceDefinitionsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.DaemonSetsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.DeploymentsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.EndpointsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.IngressProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.JobsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacedPodsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NodesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumeClaimsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.PersistentVolumesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.SecretsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.ServicesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.StatefulSetsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.StorageClassesProvider
import org.jboss.tools.intellij.kubernetes.model.resourceName
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIGURATION
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CONFIG_MAPS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CRONJOBS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.CUSTOM_RESOURCES_DEFINITIONS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.DAEMONSETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.DEPLOYMENTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.ENDPOINTS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.INGRESS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.JOBS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NAMESPACES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NETWORK
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.NODES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUMES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PERSISTENT_VOLUME_CLAIMS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.PODS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SECRETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.SERVICES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STATEFULSETS
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.STORAGE_CLASSES
import org.jboss.tools.intellij.kubernetes.tree.KubernetesStructure.Folders.WORKLOADS
import org.jboss.tools.intellij.kubernetes.tree.TreeStructure.Folder

class KubernetesStructure(model: IResourceModel) : AbstractTreeStructureContribution(model) {
    object Folders {
        val NAMESPACES = Folder("Namespaces", NamespacesProvider.KIND)
        val NODES = Folder("Nodes", NodesProvider.KIND)
        val WORKLOADS = Folder("Workloads", null)
			val DEPLOYMENTS = Folder("Deployments", DeploymentsProvider.KIND) //  Workloads / Deployments
			val STATEFULSETS = Folder("StatefulSets", StatefulSetsProvider.KIND) //  Workloads / StatefulSets
			val DAEMONSETS = Folder("DaemonSets", DaemonSetsProvider.KIND) //  Workloads / Pods
			val JOBS = Folder("Jobs", JobsProvider.KIND) //  Workloads / StatefulSets
			val CRONJOBS = Folder("CronJobs", CronJobsProvider.KIND) //  Workloads / StatefulSets
            val PODS = Folder("Pods", NamespacedPodsProvider.KIND) //  Workloads / Pods
        val NETWORK = Folder("Network", null)
            val SERVICES = Folder("Services", ServicesProvider.KIND) // Network / Services
            val ENDPOINTS = Folder("Endpoints", EndpointsProvider.KIND) // Network / Endpoints
			val INGRESS = Folder("Ingress", IngressProvider.KIND) // Network / Ingress
		val STORAGE = Folder("Storage", null)
			val PERSISTENT_VOLUMES = Folder("Persistent Volumes", PersistentVolumesProvider.KIND) // Storage / Persistent Volumes
			val PERSISTENT_VOLUME_CLAIMS = Folder("Persistent Volume Claims", PersistentVolumeClaimsProvider.KIND) // Storage / Persistent Volume Claims
			val STORAGE_CLASSES = Folder("Storage Classes", StorageClassesProvider.KIND) // Storage / Storage Classes
		val CONFIGURATION = Folder("Configuration", null)
			val CONFIG_MAPS = Folder("Config Maps", ConfigMapsProvider.KIND) // Configuration / Config Maps
			val SECRETS = Folder("Secrets", SecretsProvider.KIND) // Configuration / Secrets
		val CUSTOM_RESOURCES_DEFINITIONS = Folder("Custom Resources", CustomResourceDefinitionsProvider.KIND)
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
			val node = elementsTree.find { it.isAnchor(element) } ?: return getRootElement()
			return node.getParentElements(element)
		} catch (e: ResourceException) {
			return null
		}
	}

	override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*>? {
		return KubernetesDescriptors.createDescriptor(element, parent, model)
	}

	override fun canContribute() = true

	private fun createNamespacesElements(): Array<ElementNode<*>> {
		return arrayOf(
				element<Any> {
					anchor { it == NAMESPACES }
					childElements {
						model.resources(NamespacesProvider.KIND)
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
						KubernetesDescriptors.createPodDescriptorsFactories(it)
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
						model.resources(NodesProvider.KIND)
								.inNoNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { getRootElement() }
				},
				element<Any> {
					anchor { it is Node }
					childElements {
						model.resources(AllPodsProvider.KIND)
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
						model.resources(DeploymentsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Deployment> {
					anchor { it is Deployment }
					childElements {
						model.resources(NamespacedPodsProvider.KIND)
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
						model.resources(StatefulSetsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<StatefulSet> {
					anchor { it is StatefulSet }
					childElements {
						model.resources(NamespacedPodsProvider.KIND)
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
						model.resources(DaemonSetsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<DaemonSet> {
					anchor { it is DaemonSet }
					childElements {
						model.resources(NamespacedPodsProvider.KIND)
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
						model.resources(JobsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == CRONJOBS }
					childElements {
						model.resources(CronJobsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { WORKLOADS }
				},
				element<Any> {
					anchor { it == PODS }
					childElements {
						model.resources(NamespacedPodsProvider.KIND)
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
						model.resources(ServicesProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Service> {
					anchor { it is Service }
					childElements {
						model.resources(NamespacedPodsProvider.KIND)
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
						model.resources(EndpointsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { NETWORK }
				},
				element<Any> {
					anchor { it == INGRESS }
					childElements {
						model.resources(IngressProvider.KIND)
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
						model.resources(PersistentVolumesProvider.KIND)
								.inAnyNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == PERSISTENT_VOLUME_CLAIMS }
					childElements {
						model.resources(PersistentVolumeClaimsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { STORAGE }
				},
				element<Any> {
					anchor { it == STORAGE_CLASSES }
					childElements {
						model.resources(StorageClassesProvider.KIND)
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
						model.resources(ConfigMapsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<ConfigMap> {
					anchor { it is ConfigMap }
					childElements {
						KubernetesDescriptors.createDataDescriptorFactories(it.data, it)
					}
					parentElements { CONFIGURATION }
				},
				element<Any> {
					anchor { it == SECRETS }
					childElements {
						model.resources(SecretsProvider.KIND)
								.inCurrentNamespace()
								.list()
								.sortedBy(resourceName)
					}
					parentElements { CONFIGURATION }
				},
				element<Secret>{
					anchor { it is Secret }
					childElements {
						KubernetesDescriptors.createDataDescriptorFactories(it.data, it)
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
						model.resources(CustomResourceDefinitionsProvider.KIND)
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
}