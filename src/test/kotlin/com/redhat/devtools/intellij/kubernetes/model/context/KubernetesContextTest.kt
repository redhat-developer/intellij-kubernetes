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
package com.redhat.devtools.intellij.kubernetes.model.context

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.ModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceProvider
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourcesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ServicesProvider
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.util.Clients
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import org.junit.Before
import org.junit.Test
import java.util.function.Supplier

class KubernetesContextTest {

	private val modelChange: ModelChangeObservable = mock()

	private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
	private val currentNamespace = NAMESPACE2
	private val client: NamespacedKubernetesClient = client(currentNamespace.metadata.name, allNamespaces)

	private val namespaceWatchable: Watchable<Watcher<Namespace>> = mock()
	private val namespacesWatchSupplier: Supplier<Watchable<Watcher<Namespace>>?> = Supplier { namespaceWatchable }
	private val namespacesProvider = nonNamespacedResourceProvider<Namespace, NamespacedKubernetesClient>(
			NamespacesProvider.KIND,
			allNamespaces.toList(),
			namespacesWatchSupplier)

	private val nodesProvider = nonNamespacedResourceProvider<Node, NamespacedKubernetesClient>(
			NodesProvider.KIND,
			listOf(resource("node1"),
					resource("node2"),
					resource("node3")))

	private val allPods = arrayOf(POD1, POD2, POD3)
	private val allPodsProvider = nonNamespacedResourceProvider<Pod, NamespacedKubernetesClient>(
			AllPodsProvider.KIND,
			allPods.toList())

	private val namespacedPodsProvider = namespacedResourceProvider<Pod, NamespacedKubernetesClient>(
			AllPodsProvider.KIND,
			allPods.toList(),
			currentNamespace)

	private val hasMetadata1 = resource<HasMetadata>("hasMetadata1")
	private val hasMetadata2 = resource<HasMetadata>("hasMetadata2")
	private val hasMetadataProvider: INamespacedResourcesProvider<HasMetadata, NamespacedKubernetesClient> = namespacedResourceProvider(
			ResourceKind.create(HasMetadata::class.java),
			setOf(hasMetadata1, hasMetadata2),
			currentNamespace)

	private val danglingSecretsProvider: INamespacedResourcesProvider<Secret, NamespacedKubernetesClient> = namespacedResourceProvider(
		ResourceKind.create(Secret::class.java),
		setOf(resource("secret1")),
		currentNamespace)

	private val namespacedDefinition = customResourceDefinition(
		"namespaced crd","version1", "group1", "namespaced-crd", "Namespaced")
	private val clusterwideDefinition = customResourceDefinition(
		"cluster crd", "version2", "group2", "cluster-crd", "Cluster")
	private val customResourceDefinitionsProvider: INonNamespacedResourcesProvider<CustomResourceDefinition, NamespacedKubernetesClient> =
		nonNamespacedResourceProvider(
			ResourceKind.create(CustomResourceDefinition::class.java),
			listOf(namespacedDefinition, clusterwideDefinition))

	private val namespacedCustomResource1 = customResource("genericCustomResource1", "namespace1", namespacedDefinition)
	private val namespacedCustomResource2 = customResource("genericCustomResource2", "namespace1", namespacedDefinition)
	private val watchable1: Watchable<Watcher<GenericCustomResource>> = mock()
	private val watchableSupplier1: Supplier<Watchable<Watcher<GenericCustomResource>>?> = Supplier { watchable1 }
	private val namespacedCustomResourcesProvider: INamespacedResourcesProvider<GenericCustomResource, NamespacedKubernetesClient> =
			namespacedResourceProvider(
					ResourceKind.create(namespacedDefinition.spec),
					listOf(namespacedCustomResource1, namespacedCustomResource2),
					currentNamespace,
					watchableSupplier1)
	private val nonNamespacedCustomResource1 = resource<GenericCustomResource>("genericCustomResource1", "smurfington")
	private val watchable2: Watchable<Watcher<GenericCustomResource>> = mock()
	private val watchableSupplier2: Supplier<Watchable<Watcher<GenericCustomResource>>?> = Supplier { watchable2 }
	private val nonNamespacedCustomResourcesProvider: INonNamespacedResourcesProvider<GenericCustomResource, NamespacedKubernetesClient> =
			nonNamespacedResourceProvider(
					ResourceKind.create(GenericCustomResource::class.java),
					listOf(nonNamespacedCustomResource1),
					watchableSupplier2)

	private val watchable3: Watchable<Watcher<in HasMetadata>> = mock()
	private val watchableSupplier3: () -> Watchable<Watcher<in HasMetadata>>? = { watchable3 }

	private val resourceWatch: ResourceWatch = mock()
	private val notification: Notification = mock()

	private lateinit var context: TestableKubernetesContext

	@Before
	fun before() {
		context = createContext()
	}

	private fun createContext(): TestableKubernetesContext {
		val internalResourcesProviders = mutableListOf(
				namespacesProvider,
				nodesProvider,
				namespacedPodsProvider,
				allPodsProvider,
				customResourceDefinitionsProvider)
		val extensionResourceProviders = mutableListOf(
				hasMetadataProvider)
		return spy(TestableKubernetesContext(
				modelChange,
				this@KubernetesContextTest.client,
				internalResourcesProviders,
				extensionResourceProviders,
				Pair(namespacedCustomResourcesProvider, nonNamespacedCustomResourcesProvider),
				resourceWatch,
				notification)
		)
	}

	@Test
	fun `#setCurrentNamespace should remove all namespaced providers`() {
		// given
		val captor =
				argumentCaptor<List<ResourceKind<out HasMetadata>>>()
		val toRemove = context.namespacedProviders
				.map { it.value.kind }
				.toTypedArray()
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).stopWatchAll(captor.capture())
		assertThat(captor.firstValue).containsOnly(*toRemove)
	}

	@Test
	fun `#setCurrentNamespace should watch all namespaced providers that it stopped before`() {
		// given
		val captor =
			argumentCaptor<Collection<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>>()
		val removed: Collection<ResourceKind<out HasMetadata>> =
			context.namespacedProviders.values
				.map { it.kind }
		whenever(context.watch.stopWatchAll(any()))
			.doReturn(removed)
		@Suppress("UNCHECKED_CAST", "UNCHECKED_CAST")
		val reWatched: Array<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>> =
			context.namespacedProviders.values
				.filter { removed.contains(it.kind) }
				.map { Pair(it.kind, it.getWatchable()) }
				.toTypedArray() as Array<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).watchAll(captor.capture())
		assertThat(captor.firstValue).containsOnly(*reWatched)
	}

	@Test
	fun `#setCurrentNamespace should NOT watch provider that is not contained it stopped`() {
		// given
		val captor =
			argumentCaptor<Collection<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>>()
		val stopped: Collection<ResourceKind<*>> = listOf(danglingSecretsProvider.kind)
		whenever(context.watch.stopWatchAll(any()))
			.thenReturn(stopped)
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).watchAll(captor.capture())
		assertThat(captor.firstValue).isEmpty()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate nonnamespaced resource providers`() {
		// given
		val namespace = NAMESPACE1.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacesProvider, never()).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate namespaced resource providers if it didn't change`() {
		// given
		val namespace = currentNamespace.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacedPodsProvider, never()).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should fire change in current namespace`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(modelChange).fireCurrentNamespace(NAMESPACE1.metadata.name)
	}

	@Test
	fun `#setCurrentNamespace should set namespace in client`() {
		// given
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(client.configuration).namespace = NAMESPACE1.metadata.name
	}

	@Test
	fun `#getCurrentNamespace should retrieve current namespace in client`() {
		// given
		clearInvocations(client.configuration) // clear invocation when constructing context
		// when
		context.getCurrentNamespace()
		// then
		verify(client.configuration).namespace
	}

	@Test
	fun `#getCurrentNamespace should use null if no namespace set in client`() {
		// given
		whenever(client.configuration.namespace)
				.thenReturn(null)
		// when
		val namespace = context.getCurrentNamespace()
		// then
		assertThat(namespace).isNull()
	}

	@Test
	fun `#getCurrentNamespace should return null if current namespace is set but doesnt exist`() {
		// given
		whenever(client.configuration.namespace)
				.thenReturn("inexistent")

		// when
		val namespace = context.getCurrentNamespace()
		// then
		assertThat(namespace).isNull()
	}

	@Test
	fun `#isCurrentNamespace should return false if given namespace is not in existing namespaces`() {
		// given
		// when
		val isCurrent = context.isCurrentNamespace(mock())
		// then
		assertThat(isCurrent).isFalse()
	}

	@Test
	fun `#isCurrentNamespace should return false if given namespace is not current namespaces`() {
		// given
		assertThat(currentNamespace).isNotEqualTo(NAMESPACE3)
		// when
		val isCurrent = context.isCurrentNamespace(NAMESPACE3)
		// then
		assertThat(isCurrent).isFalse()
	}

	@Test
	fun `#getResources should get all resources in provider for given resource type in correct ResourcesIn type`() {
		// given
		// when
		context.getAllResources(NodesProvider.KIND, ResourcesIn.NO_NAMESPACE)
		// then
		verify(nodesProvider).allResources
	}

	@Test
	fun `#getResources should not get all resources in provider for given resource type in wrong ResourcesIn type`() {
		// given
		// when
		// there are no namespaces in current namespace
		context.getAllResources(NamespacesProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(nodesProvider, never()).allResources
	}

	@Test
	fun `#getResources should return empty list if there's no provider for given resource type in given ResourceIn type`() {
		// given
		// when
		// namespace provider exists but for ResourceIn.NO_NAMESPACE
		val resources = context.getAllResources(NamespacesProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(resources).isEmpty()
	}

	@Test
	fun `#getResources should return empty list if there's no provider of given resource type in given ResourceIn type`() {
		// given
		// when
		// services provider was not registered to context, it doesn't exist in context
		val services = context.getAllResources(ServicesProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(services).isEmpty()
	}

	@Test
	fun `#getCustomResources should query CustomResourceProvider`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		// then
		verify(namespacedCustomResourcesProvider).allResources
	}

	@Test
	fun `#getCustomResources should create CustomResourceProvider once and reuse it`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(namespacedDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(eq(namespacedDefinition), any())
	}

	@Test
	fun `#getCustomResources should create CustomResourceProvider for each unique definition`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(eq(namespacedDefinition), any())
		verify(context, times(1)).createCustomResourcesProvider(eq(clusterwideDefinition), any())
	}

	@Test(expected = IllegalArgumentException::class)
	fun `#getCustomResources should throw if scope is unknown`() {
		// given
		val bogusScope = customResourceDefinition(
				"bogus scope","version1", "group1", "bogus-scope-crd", "Bogus")
		// when
		context.getAllResources(bogusScope)
		// then
	}

	@Test
	fun `#delete should ask provider to delete`() {
		// given
		val toDelete = listOf(POD2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsProvider).delete(toDelete)
	}

	@Test
	fun `#delete should not ask any provider to delete if there is no provider for it`() {
		// given
		val toDelete = listOf(resource<HasMetadata>("lord sith"))
		// when
		context.delete(toDelete)
		// then
		verify(allPodsProvider, never()).delete(toDelete)
	}

	@Test
	fun `#delete should only delete 1 resource if it is given twice the same resource`() {
		// given
		val toDelete = listOf(POD2, POD2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsProvider, times(1)).delete(eq(listOf(POD2)))
	}

	@Test
	fun `#delete should delete in 2 separate providers if resources of 2 kinds are given`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsProvider, times(1)).delete(eq(listOf(POD2)))
		verify(namespacesProvider, times(1)).delete(eq(listOf(NAMESPACE2)))
	}

	@Test
	fun `#delete should fire if resource was deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(allPodsProvider.delete(any()))
				.thenReturn(true)
		// when
		context.delete(toDelete)
		// then
		verify(modelChange).fireModified(toDelete)
	}

	@Test
	fun `#delete should set resource deletionTimestamp if resource was deleted`() {
		// given
		val toDelete = listOf(POD2, POD3)
		// when
		context.delete(toDelete)
		// then
		verify(POD2.metadata, atLeastOnce()).setDeletionTimestamp(any())
		verify(POD3.metadata, atLeastOnce()).setDeletionTimestamp(any())
	}

	@Test(expected=MultiResourceException::class)
	fun `#delete should throw if resource was NOT deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(allPodsProvider.delete(any()))
			.thenReturn(false)
		// when
		context.delete(toDelete)
		// then
		// expect exception
	}

	@Test
	fun `#delete should throw for provider that failed, not successful ones`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		whenever(allPodsProvider.delete(any()))
			.thenReturn(false)
		// when
		val ex = try {
			context.delete(toDelete)
			null
		} catch (e: MultiResourceException) {
			e
		}
		// then
		assertThat(ex?.causes?.size).isEqualTo(1)
		val resourceEx = ex?.causes?.toTypedArray()!![0]
		assertThat(resourceEx.resources).containsExactly(POD2)
	}

	@Test
	fun `#delete should NOT fire if resource was NOT deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(allPodsProvider.delete(any()))
			.thenReturn(false)
		// when
		try {
			context.delete(toDelete)
		} catch (e: MultiResourceException) {
			// ignore expected exception
		}
		// then
		verify(modelChange, never()).fireModified(toDelete)
	}

	@Test
	fun `#watch(kind) should watch watchable provided by namespaced resource provider`() {
		// given
		givenCustomResourceProvider(namespacedDefinition,
				customResourceDefinitionsProvider,
				namespacedCustomResourcesProvider)
		val watch = context.watch
		clearInvocations(watch)
		// when
		context.watch(ResourceKind.create(namespacedDefinition))
		// then
		@Suppress("UNCHECKED_CAST")
		verify(context.watch, times(1)).watch(namespacedCustomResourcesProvider.kind, watchableSupplier1
				as Supplier<Watchable<Watcher<in HasMetadata>>?>)
	}

	@Test
	fun `#watch(kind) should watch watchable provided by non-namespaced resource provider`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				nonNamespacedCustomResourcesProvider)
		// when
		context.watch(ResourceKind.create(clusterwideDefinition))
		// then
		@Suppress("UNCHECKED_CAST")
		verify(context.watch, times(1)).watch(nonNamespacedCustomResourcesProvider.kind, watchableSupplier2
				as Supplier<Watchable<Watcher<in HasMetadata>>?>)
	}

	@Test
	fun `#watch(definition) should create custom resource provider if none exists yet`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
			customResourceDefinitionsProvider,
			null) // no resource provider for definition
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#watch(definition) should NOT create custom resource provider if already exists`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
			customResourceDefinitionsProvider,
			nonNamespacedCustomResourcesProvider)
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesProvider(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#stopWatch(kind) should stop watch`() {
		// given
		// when
		context.stopWatch(NamespacesProvider.KIND)
		// then
		verify(resourceWatch, times(1)).stopWatch(NamespacesProvider.KIND)
	}

	@Test
	fun `#stopWatch(kind) should clear providers`() {
		// given
		// when
		context.stopWatch(NamespacesProvider.KIND)
		// then
		verify(namespacesProvider, times(1)).invalidate()
	}

	@Test
	fun `#stopWatch(kind) should NOT notify of changes in provider`() {
		// given
		// when
		context.stopWatch(NamespacesProvider.KIND)
		// then
		// dont notify invalidation change because this would cause UI to reload
		// and therefore to repopulate the cache immediately.
		// Any resource operation that eventually happens while the watch is not active would cause the cache
		// to become out-of-sync and it would therefore return invalid resources when asked to do so
		verify(modelChange, never()).fireModified(NamespacesProvider.KIND)
	}

	@Test
	fun `#stopWatch(definition) should stop watching) kind specified by definition`() {
		// given
		val definition = clusterwideDefinition
		val kind = ResourceKind.create(definition.spec)
		// when
		context.stopWatch(definition)
		// then
		verify(context, times(1)).stopWatch(kind)
	}

	@Test
	fun `#add(namespace) should add to namespaces provider but not to pods provider`() {
		// given
		val namespace = resource<Namespace>("papa smurf namespace")
		// when
		context.add(namespace)
		// then
		verify(namespacesProvider).add(namespace)
		verify(namespacedPodsProvider, never()).add(namespace)
	}

	@Test
	fun `#add(pod) should add pod in current namespace to pods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.add(pod)
		// then
		verify(namespacedPodsProvider).add(pod)
	}

	@Test
	fun `#add(pod) should add pod to allPods provider`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.add(pod)
		// then
		verify(allPodsProvider).add(pod)
	}

	@Test
	fun `#add(pod) should not add pod of non-current namespace to namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.add(pod)
		// then
		verify(namespacedPodsProvider, never()).add(pod)
	}

	@Test
	fun `#add(pod) should return true if pod was added to pods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		whenever(namespacedPodsProvider.add(pod))
			.thenReturn(true)

		// when
		val added = context.add(pod)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(namespace) should return true if namespace was added to namespace provider`() {
		// given
		val namespace = resource<Namespace>("pod")
		whenever(namespacesProvider.add(namespace))
			.thenReturn(true)
		// when
		val added = context.add(namespace)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(pod) should return false if pod was not added to pods provider`() {
		// given
		val pod = resource<Pod>("pod")
		whenever(namespacedPodsProvider.add(pod))
			.thenReturn(false)
		// when
		val added = context.add(pod)
		// then
		assertThat(added).isFalse()
	}

	@Test
	fun `#add(pod) should fire if provider added pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		whenever(namespacedPodsProvider.add(pod))
			.thenReturn(true)
		// when
		context.add(pod)
		// then
		verify(modelChange).fireAdded(pod)
	}

	@Test
	fun `#add(pod) should not fire if provider did not add pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsProvider.add(pod))
			.thenReturn(false)
		// when
		context.add(pod)
		// then
		verify(modelChange, never()).fireAdded(pod)
	}

	@Test
	fun `#add(CustomResourceDefinition) should create namespaced custom resources provider if definition was added`() {
		// given
		val currentNamespace = currentNamespace.metadata.name
		setNamespaceForResource(currentNamespace, namespacedDefinition)
		whenever(customResourceDefinitionsProvider.add(namespacedDefinition))
				.doReturn(true)
		clearInvocations(context)
		// when
		context.add(namespacedDefinition)
		// then
		verify(context)
				.createCustomResourcesProvider(
						eq(namespacedDefinition),
						eq(ResourcesIn.CURRENT_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should create clusterwide custom resources provider if definition was added`() {
		// given
		whenever(customResourceDefinitionsProvider.add(clusterwideDefinition))
				.doReturn(true)
		// when
		context.add(clusterwideDefinition)
		// then
		verify(context, times(1))
				.createCustomResourcesProvider(
						eq(clusterwideDefinition),
						eq(ResourcesIn.NO_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should NOT create custom resources provider if definition was NOT added`() {
		// given
		whenever(namespacedCustomResourcesProvider.add(clusterwideDefinition))
				.doReturn(false)
		// when
		context.add(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesProvider(eq(clusterwideDefinition), any())
	}

	@Test
	fun `#remove(pod) should remove pod from pods provider but not from namespace provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider).remove(pod)
		verify(namespacesProvider, never()).remove(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in current namespace from allPods && namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider).remove(pod)
		verify(allPodsProvider).remove(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in non-current namespace from allPods provider but not from namespacedPods provider`() {
		// given
		val pod = resource<Pod>("pod", "42")
		// when
		context.remove(pod)
		// then
		verify(namespacedPodsProvider, never()).remove(pod)
		verify(allPodsProvider).remove(pod)
	}

	@Test
	fun `#remove(namespace) should remove from namespaces provider but not from pods provider`() {
		// given
		val namespace = NAMESPACE1
		// when
		context.remove(namespace)
		// then
		verify(namespacesProvider).remove(namespace)
		verify(namespacedPodsProvider, never()).remove(namespace)
	}

	@Test
	fun `#remove(pod) should fire if provider removed pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		whenever(namespacedPodsProvider.remove(pod))
			.thenReturn(true)
		// when
		context.remove(pod)
		// then
		verify(modelChange).fireRemoved(pod)
	}

	@Test
	fun `#remove(pod) should not fire if provider did not remove pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsProvider.remove(pod))
			.thenReturn(false)
		// when
		context.remove(pod)
		// then
		verify(modelChange, never()).fireRemoved(pod)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove clusterwide custom resource provider when definition is removed`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				nonNamespacedCustomResourcesProvider)
		// when
		context.remove(clusterwideDefinition)
		// then
		assertThat(context.nonNamespacedProviders)
				.doesNotContainValue(nonNamespacedCustomResourcesProvider)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should stop watch when definition is removed `() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				nonNamespacedCustomResourcesProvider)
		clearInvocations(resourceWatch)
		// when
		context.remove(clusterwideDefinition)
		// then
		verify(resourceWatch).stopWatch(nonNamespacedCustomResourcesProvider.kind)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove namespaced custom resource provider when definition is removed`() {
		// given
		givenCustomResourceProvider(namespacedDefinition,
				customResourceDefinitionsProvider,
				namespacedCustomResourcesProvider)
		// when
		context.remove(namespacedDefinition)
		// then
		assertThat(context.namespacedProviders)
				.doesNotContainValue(namespacedCustomResourcesProvider)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should NOT remove namespaced custom resource provider when definition is NOT removed`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				namespacedCustomResourcesProvider)
		whenever(customResourceDefinitionsProvider.remove(clusterwideDefinition))
				.doReturn(false) // was not removed
		// when
		context.remove(clusterwideDefinition)
		// then
		assertThat(context.namespacedProviders)
				.containsValue(namespacedCustomResourcesProvider)
	}

	@Test
	fun `#invalidate() should invalidate all resource providers`() {
		// given
		// when
		context.invalidate()
		// then
		verify(namespacesProvider).invalidate()
		verify(namespacedPodsProvider).invalidate()
	}

	@Test
	fun `#invalidate(kind) should invalidate resource provider for this kind`() {
		// given
		// when
		context.invalidate(namespacedPodsProvider.kind)
		// then
		verify(namespacesProvider, never()).invalidate()
		verify(namespacedPodsProvider).invalidate()
	}

	@Test
	fun `#replace(resource) should replace in namespaced resource provider`() {
		// given
		val pod = allPods[0]
		// when
		context.replace(pod)
		// then
		verify(namespacesProvider, never()).replace(pod)
		verify(namespacedPodsProvider).replace(pod)
	}

	@Test
	fun `#replace(resource) should replace in non-namespaced resource provider`() {
		// given
		val namespace = allNamespaces[0]
		// when
		context.replace(namespace)
		// then
		verify(namespacesProvider).replace(namespace)
		verify(namespacedPodsProvider, never()).replace(namespace)
	}

	@Test
	fun `#replace(customResource) should replace in custom resource provider`() {
		// given
		givenCustomResourceProvider(namespacedDefinition,
				customResourceDefinitionsProvider,
				namespacedCustomResourcesProvider)
		// when
		context.replace(namespacedCustomResource1)
		// then
		verify(namespacedCustomResourcesProvider).replace(namespacedCustomResource1)
	}

	@Test
	fun `#replace(pod) should fire if provider replaced pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsProvider.replace(pod))
			.thenReturn(true)
		// when
		context.replace(pod)
		// then
		verify(modelChange).fireModified(pod)
	}

	@Test
	fun `#replace(pod) should not fire if provider did NOT replace pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsProvider.replace(pod))
			.thenReturn(false)
		// when
		context.replace(pod)
		// then
		verify(modelChange, never()).fireModified(pod)
	}

	@Test
	fun `#close should close client`() {
		// given
		// when
		context.close()
		// then
		verify(client).close()
	}

	private fun givenCustomResourceProvider(
			definition: CustomResourceDefinition,
			definitionProvider: IResourcesProvider<CustomResourceDefinition>,
			resourceProvider: IResourcesProvider<GenericCustomResource>?) {
		whenever(definitionProvider.remove(definition))
			.doReturn(true)

		val kind = ResourceKind.create(definition.spec)
		if (resourceProvider != null) {
			whenever(resourceProvider.kind)
				.doReturn(kind)
		}
		if (resourceProvider is INamespacedResourcesProvider<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.namespacedProviders[kind] =
				resourceProvider as INamespacedResourcesProvider<*, NamespacedKubernetesClient>
		} else if (resourceProvider is INonNamespacedResourcesProvider<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.nonNamespacedProviders[kind] =
				resourceProvider as INonNamespacedResourcesProvider<*, NamespacedKubernetesClient>
		}
	}

	private fun setNamespaceForResource(namespace: String?, resource: HasMetadata) {
		whenever(resource.metadata.namespace)
				.doReturn(namespace)
	}

	class TestableKubernetesContext(
		observable: ModelChangeObservable,
		client: NamespacedKubernetesClient,
		private val internalResourceProviders: List<IResourcesProvider<out HasMetadata>>,
		private val extensionResourcesProviders: List<IResourcesProvider<out HasMetadata>>,
		private val customResourcesProviders: Pair<
					INamespacedResourcesProvider<GenericCustomResource, NamespacedKubernetesClient>,
					INonNamespacedResourcesProvider<GenericCustomResource, NamespacedKubernetesClient>>,
		public override var watch: ResourceWatch,
		override val notification: Notification)
		: KubernetesContext(observable, client, mock()) {

		public override val namespacedProviders
				: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourcesProvider<*, NamespacedKubernetesClient>>
			get() {
				return super.namespacedProviders
			}

		public override val nonNamespacedProviders
				: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourcesProvider<*, *>>
			get() {
				return super.nonNamespacedProviders
			}

		override fun getInternalResourceProviders(supplier: Clients<NamespacedKubernetesClient>)
				: List<IResourcesProvider<out HasMetadata>> {
			return internalResourceProviders
		}

		override fun getExtensionResourceProviders(supplier: Clients<NamespacedKubernetesClient>)
				: List<IResourcesProvider<out HasMetadata>> {
			return extensionResourcesProviders
		}

		public override fun createCustomResourcesProvider(
			definition: CustomResourceDefinition,
			resourceIn: ResourcesIn)
				: IResourcesProvider<GenericCustomResource> {
			return when(resourceIn) {
				ResourcesIn.CURRENT_NAMESPACE ->
					customResourcesProviders.first
				ResourcesIn.NO_NAMESPACE,
				ResourcesIn.ANY_NAMESPACE ->
					customResourcesProviders.second
			}
		}

	}
}
