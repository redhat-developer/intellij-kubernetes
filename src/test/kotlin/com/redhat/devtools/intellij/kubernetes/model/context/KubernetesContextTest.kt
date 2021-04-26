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
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.AllPodsOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NamespacesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.NodesOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.ServicesOperator
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
	private val namespacesOperator = nonNamespacedResourceOperator<Namespace, NamespacedKubernetesClient>(
			NamespacesOperator.KIND,
			allNamespaces.toList(),
			namespacesWatchSupplier)

	private val nodesOperator = nonNamespacedResourceOperator<Node, NamespacedKubernetesClient>(
			NodesOperator.KIND,
			listOf(resource("node1"),
					resource("node2"),
					resource("node3")))

	private val allPods = arrayOf(POD1, POD2, POD3)
	private val allPodsOperator = nonNamespacedResourceOperator<Pod, NamespacedKubernetesClient>(
			AllPodsOperator.KIND,
			allPods.toList())

	private val namespacedPodsOperator = namespacedResourceOperator<Pod, NamespacedKubernetesClient>(
			AllPodsOperator.KIND,
			allPods.toList(),
			currentNamespace)

	private val hasMetadata1 = resource<HasMetadata>("hasMetadata1")
	private val hasMetadata2 = resource<HasMetadata>("hasMetadata2")
	private val hasMetadataOperator: INamespacedResourceOperator<HasMetadata, NamespacedKubernetesClient> = namespacedResourceOperator(
			ResourceKind.create(HasMetadata::class.java),
			setOf(hasMetadata1, hasMetadata2),
			currentNamespace)

	private val danglingSecretsOperator: INamespacedResourceOperator<Secret, NamespacedKubernetesClient> = namespacedResourceOperator(
		ResourceKind.create(Secret::class.java),
		setOf(resource("secret1")),
		currentNamespace)

	private val namespacedDefinition = customResourceDefinition(
		"namespaced crd","version1", "group1", "namespaced-crd", "Namespaced")
	private val clusterwideDefinition = customResourceDefinition(
		"cluster crd", "version2", "group2", "cluster-crd", "Cluster")
	private val customResourceDefinitionsOperator: INonNamespacedResourceOperator<CustomResourceDefinition, NamespacedKubernetesClient> =
		nonNamespacedResourceOperator(
			ResourceKind.create(CustomResourceDefinition::class.java),
			listOf(namespacedDefinition, clusterwideDefinition))

	private val namespacedCustomResource1 = customResource("genericCustomResource1", "namespace1", namespacedDefinition)
	private val namespacedCustomResource2 = customResource("genericCustomResource2", "namespace1", namespacedDefinition)
	private val watchable1: Watchable<Watcher<GenericCustomResource>> = mock()
	private val watchableSupplier1: Supplier<Watchable<Watcher<GenericCustomResource>>?> = Supplier { watchable1 }
	private val namespacedCustomResourceOperator: INamespacedResourceOperator<GenericCustomResource, NamespacedKubernetesClient> =
			namespacedResourceOperator(
					ResourceKind.create(namespacedDefinition.spec),
					listOf(namespacedCustomResource1, namespacedCustomResource2),
					currentNamespace,
					watchableSupplier1)
	private val nonNamespacedCustomResource1 = resource<GenericCustomResource>("genericCustomResource1", "smurfington")
	private val watchable2: Watchable<Watcher<GenericCustomResource>> = mock()
	private val watchableSupplier2: Supplier<Watchable<Watcher<GenericCustomResource>>?> = Supplier { watchable2 }
	private val nonNamespacedCustomResourcesOperator: INonNamespacedResourceOperator<GenericCustomResource, NamespacedKubernetesClient> =
			nonNamespacedResourceOperator(
					ResourceKind.create(GenericCustomResource::class.java),
					listOf(nonNamespacedCustomResource1),
					watchableSupplier2)

	private val watchable3: Watchable<Watcher<in HasMetadata>> = mock()
	private val watchableSupplier3: () -> Watchable<Watcher<in HasMetadata>>? = { watchable3 }

	private val resourceWatch: ResourceWatch = mock()
	private val watchListeners: ResourceWatch.WatchListeners = mock()
	private val notification: Notification = mock()

	private lateinit var context: TestableKubernetesContext

	@Before
	fun before() {
		context = createContext()
	}

	private fun createContext(): TestableKubernetesContext {
		val internalResourcesOperators = mutableListOf(
				namespacesOperator,
				nodesOperator,
				namespacedPodsOperator,
				allPodsOperator,
				customResourceDefinitionsOperator)
		val extensionResourceOperators = mutableListOf(
				hasMetadataOperator)
		return spy(TestableKubernetesContext(
				modelChange,
				this@KubernetesContextTest.client,
				internalResourcesOperators,
				extensionResourceOperators,
				Pair(namespacedCustomResourceOperator, nonNamespacedCustomResourcesOperator),
				resourceWatch,
				notification)
		)
	}

	@Test
	fun `#setCurrentNamespace should remove all namespaced operators`() {
		// given
		val captor =
				argumentCaptor<List<ResourceKind<out HasMetadata>>>()
		val toRemove = context.namespacedOperators
				.map { it.value.kind }
				.toTypedArray()
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).stopWatchAll(captor.capture())
		assertThat(captor.firstValue).containsOnly(*toRemove)
	}

	@Test
	fun `#setCurrentNamespace should watch all namespaced operators that it stopped before`() {
		// given
		val captor =
			argumentCaptor<Collection<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>>()
		val removed: Collection<ResourceKind<out HasMetadata>> =
			context.namespacedOperators.values
				.map { it.kind }
		whenever(context.watch.stopWatchAll(any()))
			.doReturn(removed)
		@Suppress("UNCHECKED_CAST", "UNCHECKED_CAST")
		val reWatched: Array<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>> =
			context.namespacedOperators.values
				.filter { removed.contains(it.kind) }
				.map { Pair(it.kind, it.getKindWatchable()) }
				.toTypedArray() as Array<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).watchAll(captor.capture(), watchListeners)
		assertThat(captor.firstValue).containsOnly(*reWatched)
	}

	@Test
	fun `#setCurrentNamespace should NOT watch operator that is not contained it stopped`() {
		// given
		val captor =
			argumentCaptor<Collection<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watcher<in HasMetadata>>?>>>>()
		val stopped: Collection<ResourceKind<*>> = listOf(danglingSecretsOperator.kind)
		whenever(context.watch.stopWatchAll(any()))
			.thenReturn(stopped)
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).watchAll(captor.capture(), watchListeners)
		assertThat(captor.firstValue).isEmpty()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate nonnamespaced resource operators`() {
		// given
		val namespace = NAMESPACE1.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacesOperator, never()).invalidate()
	}

	@Test
	fun `#setCurrentNamespace should not invalidate namespaced resource operators if it didn't change`() {
		// given
		val namespace = currentNamespace.metadata.name
		// when
		context.setCurrentNamespace(namespace)
		// then
		verify(namespacedPodsOperator, never()).invalidate()
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
	fun `#getResources should get all resources in operator for given resource type in correct ResourcesIn type`() {
		// given
		// when
		context.getAllResources(NodesOperator.KIND, ResourcesIn.NO_NAMESPACE)
		// then
		verify(nodesOperator).allResources
	}

	@Test
	fun `#getResources should not get all resources in operator for given resource type in wrong ResourcesIn type`() {
		// given
		// when
		// there are no namespaces in current namespace
		context.getAllResources(NamespacesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(nodesOperator, never()).allResources
	}

	@Test
	fun `#getResources should return empty list if there's no operator for given resource type in given ResourceIn type`() {
		// given
		// when
		// namespace operator exists but for ResourceIn.NO_NAMESPACE
		val resources = context.getAllResources(NamespacesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(resources).isEmpty()
	}

	@Test
	fun `#getResources should return empty list if there's no operator of given resource type in given ResourceIn type`() {
		// given
		// when
		// services operator was not registered to context, it doesn't exist in context
		val services = context.getAllResources(ServicesOperator.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		assertThat(services).isEmpty()
	}

	@Test
	fun `#getCustomResources should query CustomResourceOperator`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		// then
		verify(namespacedCustomResourceOperator).allResources
	}

	@Test
	fun `#getCustomResources should create CustomResourceOperator once and reuse it`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(namespacedDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(eq(namespacedDefinition), any())
	}

	@Test
	fun `#getCustomResources should create CustomResourceOperator for each unique definition`() {
		// given
		// when
		context.getAllResources(namespacedDefinition)
		context.getAllResources(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(eq(namespacedDefinition), any())
		verify(context, times(1)).createCustomResourcesOperator(eq(clusterwideDefinition), any())
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
	fun `#delete should ask operator to delete`() {
		// given
		val toDelete = listOf(POD2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsOperator).delete(toDelete)
	}

	@Test
	fun `#delete should not ask any operator to delete if there is no operator for it`() {
		// given
		val toDelete = listOf(resource<HasMetadata>("lord sith"))
		// when
		context.delete(toDelete)
		// then
		verify(allPodsOperator, never()).delete(toDelete)
	}

	@Test
	fun `#delete should only delete 1 resource if it is given twice the same resource`() {
		// given
		val toDelete = listOf(POD2, POD2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsOperator, times(1)).delete(eq(listOf(POD2)))
	}

	@Test
	fun `#delete should delete in 2 separate operators if resources of 2 kinds are given`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		// when
		context.delete(toDelete)
		// then
		verify(allPodsOperator, times(1)).delete(eq(listOf(POD2)))
		verify(namespacesOperator, times(1)).delete(eq(listOf(NAMESPACE2)))
	}

	@Test
	fun `#delete should fire if resource was deleted`() {
		// given
		val toDelete = listOf(POD2)
		whenever(allPodsOperator.delete(any()))
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
		whenever(allPodsOperator.delete(any()))
			.thenReturn(false)
		// when
		context.delete(toDelete)
		// then
		// expect exception
	}

	@Test
	fun `#delete should throw for operator that failed, not successful ones`() {
		// given
		val toDelete = listOf(POD2, NAMESPACE2)
		whenever(allPodsOperator.delete(any()))
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
		whenever(allPodsOperator.delete(any()))
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
	fun `#watch(kind) should watch watchable provided by namespaced resource operator`() {
		// given
		givenCustomResourceOperator(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		val watch = context.watch
		clearInvocations(watch)
		// when
		context.watch(ResourceKind.create(namespacedDefinition))
		// then
		@Suppress("UNCHECKED_CAST")
		verify(context.watch, times(1)).watch(namespacedCustomResourceOperator.kind,
			watchableSupplier1 as Supplier<Watchable<Watcher<in HasMetadata>>?>,
			watchListeners)
	}

	@Test
	fun `#watch(kind) should watch watchable provided by non-namespaced resource operator`() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		// when
		context.watch(ResourceKind.create(clusterwideDefinition))
		// then
		@Suppress("UNCHECKED_CAST")
		verify(context.watch, times(1)).watch(nonNamespacedCustomResourcesOperator.kind,
			watchableSupplier2 as Supplier<Watchable<Watcher<in HasMetadata>>?>,
			watchListeners)
	}

	@Test
	fun `#watch(definition) should create custom resource operator if none exists yet`() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
			customResourceDefinitionsOperator,
			null) // no resource operator for definition
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesOperator(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#watch(definition) should NOT create custom resource operator if already exists`() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
			customResourceDefinitionsOperator,
			nonNamespacedCustomResourcesOperator)
		// when
		context.watch(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesOperator(
			eq(clusterwideDefinition),
			any()
		)
	}

	@Test
	fun `#stopWatch(kind) should stop watch`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		verify(resourceWatch, times(1)).stopWatch(NamespacesOperator.KIND)
	}

	@Test
	fun `#stopWatch(kind) should clear operators`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		verify(namespacesOperator, times(1)).invalidate()
	}

	@Test
	fun `#stopWatch(kind) should NOT notify of changes in operator`() {
		// given
		// when
		context.stopWatch(NamespacesOperator.KIND)
		// then
		// dont notify invalidation change because this would cause UI to reload
		// and therefore to repopulate the cache immediately.
		// Any resource operation that eventually happens while the watch is not active would cause the cache
		// to become out-of-sync and it would therefore return invalid resources when asked to do so
		verify(modelChange, never()).fireModified(NamespacesOperator.KIND)
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
	fun `#add(namespace) should add to namespaces operator but not to pods operator`() {
		// given
		val namespace = resource<Namespace>("papa smurf namespace")
		// when
		context.added(namespace)
		// then
		verify(namespacesOperator).added(namespace)
		verify(namespacedPodsOperator, never()).added(namespace)
	}

	@Test
	fun `#add(pod) should add pod in current namespace to pods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.added(pod)
		// then
		verify(namespacedPodsOperator).added(pod)
	}

	@Test
	fun `#add(pod) should add pod to allPods operator`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.added(pod)
		// then
		verify(allPodsOperator).added(pod)
	}

	@Test
	fun `#add(pod) should not add pod of non-current namespace to namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", "gargamel namespace")
		// when
		context.added(pod)
		// then
		verify(namespacedPodsOperator, never()).added(pod)
	}

	@Test
	fun `#add(pod) should return true if pod was added to pods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(true)

		// when
		val added = context.added(pod)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(namespace) should return true if namespace was added to namespace operator`() {
		// given
		val namespace = resource<Namespace>("pod")
		whenever(namespacesOperator.added(namespace))
			.thenReturn(true)
		// when
		val added = context.added(namespace)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(pod) should return false if pod was not added to pods operator`() {
		// given
		val pod = resource<Pod>("pod")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(false)
		// when
		val added = context.added(pod)
		// then
		assertThat(added).isFalse()
	}

	@Test
	fun `#add(pod) should fire if operator added pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(true)
		// when
		context.added(pod)
		// then
		verify(modelChange).fireAdded(pod)
	}

	@Test
	fun `#add(pod) should not fire if operator did not add pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsOperator.added(pod))
			.thenReturn(false)
		// when
		context.added(pod)
		// then
		verify(modelChange, never()).fireAdded(pod)
	}

	@Test
	fun `#add(CustomResourceDefinition) should create namespaced custom resources operator if definition was added`() {
		// given
		val currentNamespace = currentNamespace.metadata.name
		setNamespaceForResource(currentNamespace, namespacedDefinition)
		whenever(customResourceDefinitionsOperator.added(namespacedDefinition))
				.doReturn(true)
		clearInvocations(context)
		// when
		context.added(namespacedDefinition)
		// then
		verify(context)
				.createCustomResourcesOperator(
						eq(namespacedDefinition),
						eq(ResourcesIn.CURRENT_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should create clusterwide custom resources operator if definition was added`() {
		// given
		whenever(customResourceDefinitionsOperator.added(clusterwideDefinition))
				.doReturn(true)
		// when
		context.added(clusterwideDefinition)
		// then
		verify(context, times(1))
				.createCustomResourcesOperator(
						eq(clusterwideDefinition),
						eq(ResourcesIn.NO_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should NOT create custom resources operator if definition was NOT added`() {
		// given
		whenever(namespacedCustomResourceOperator.added(clusterwideDefinition))
				.doReturn(false)
		// when
		context.added(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesOperator(eq(clusterwideDefinition), any())
	}

	@Test
	fun `#remove(pod) should remove pod from pods operator but not from namespace operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator).removed(pod)
		verify(namespacesOperator, never()).removed(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in current namespace from allPods && namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", currentNamespace.metadata.name)
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator).removed(pod)
		verify(allPodsOperator).removed(pod)
	}

	@Test
	fun `#remove(pod) should remove pod in non-current namespace from allPods operator but not from namespacedPods operator`() {
		// given
		val pod = resource<Pod>("pod", "42")
		// when
		context.removed(pod)
		// then
		verify(namespacedPodsOperator, never()).removed(pod)
		verify(allPodsOperator).removed(pod)
	}

	@Test
	fun `#remove(namespace) should remove from namespaces operator but not from pods operator`() {
		// given
		val namespace = NAMESPACE1
		// when
		context.removed(namespace)
		// then
		verify(namespacesOperator).removed(namespace)
		verify(namespacedPodsOperator, never()).removed(namespace)
	}

	@Test
	fun `#remove(pod) should fire if operator removed pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		whenever(namespacedPodsOperator.removed(pod))
			.thenReturn(true)
		// when
		context.removed(pod)
		// then
		verify(modelChange).fireRemoved(pod)
	}

	@Test
	fun `#remove(pod) should not fire if operator did not remove pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsOperator.removed(pod))
			.thenReturn(false)
		// when
		context.removed(pod)
		// then
		verify(modelChange, never()).fireRemoved(pod)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove clusterwide custom resource operator when definition is removed`() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		// when
		context.removed(clusterwideDefinition)
		// then
		assertThat(context.nonNamespacedOperators)
				.doesNotContainValue(nonNamespacedCustomResourcesOperator)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should stop watch when definition is removed `() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
				customResourceDefinitionsOperator,
				nonNamespacedCustomResourcesOperator)
		clearInvocations(resourceWatch)
		// when
		context.removed(clusterwideDefinition)
		// then
		verify(resourceWatch).stopWatch(nonNamespacedCustomResourcesOperator.kind)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove namespaced custom resource operator when definition is removed`() {
		// given
		givenCustomResourceOperator(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		// when
		context.removed(namespacedDefinition)
		// then
		assertThat(context.namespacedOperators)
				.doesNotContainValue(namespacedCustomResourceOperator)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should NOT remove namespaced custom resource operator when definition is NOT removed`() {
		// given
		givenCustomResourceOperator(clusterwideDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		whenever(customResourceDefinitionsOperator.removed(clusterwideDefinition))
				.doReturn(false) // was not removed
		// when
		context.removed(clusterwideDefinition)
		// then
		assertThat(context.namespacedOperators)
				.containsValue(namespacedCustomResourceOperator)
	}

	@Test
	fun `#invalidate() should invalidate all resource operators`() {
		// given
		// when
		context.invalidate()
		// then
		verify(namespacesOperator).invalidate()
		verify(namespacedPodsOperator).invalidate()
	}

	@Test
	fun `#invalidate(kind) should invalidate resource operator for this kind`() {
		// given
		// when
		context.invalidate(namespacedPodsOperator.kind)
		// then
		verify(namespacesOperator, never()).invalidate()
		verify(namespacedPodsOperator).invalidate()
	}

	@Test
	fun `#replace(resource) should replace in namespaced resource operator`() {
		// given
		val pod = allPods[0]
		// when
		context.replaced(pod)
		// then
		verify(namespacesOperator, never()).replaced(pod)
		verify(namespacedPodsOperator).replaced(pod)
	}

	@Test
	fun `#replace(resource) should replace in non-namespaced resource operator`() {
		// given
		val namespace = allNamespaces[0]
		// when
		context.replaced(namespace)
		// then
		verify(namespacesOperator).replaced(namespace)
		verify(namespacedPodsOperator, never()).replaced(namespace)
	}

	@Test
	fun `#replace(customResource) should replace in custom resource operator`() {
		// given
		givenCustomResourceOperator(namespacedDefinition,
				customResourceDefinitionsOperator,
				namespacedCustomResourceOperator)
		// when
		context.replaced(namespacedCustomResource1)
		// then
		verify(namespacedCustomResourceOperator).replaced(namespacedCustomResource1)
	}

	@Test
	fun `#replace(pod) should fire if operator replaced pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsOperator.replaced(pod))
			.thenReturn(true)
		// when
		context.replaced(pod)
		// then
		verify(modelChange).fireModified(pod)
	}

	@Test
	fun `#replace(pod) should not fire if operator did NOT replace pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		whenever(namespacedPodsOperator.replaced(pod))
			.thenReturn(false)
		// when
		context.replaced(pod)
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

	private fun givenCustomResourceOperator(
		definition: CustomResourceDefinition,
		definitionOperator: IResourceOperator<CustomResourceDefinition>,
		resourceOperator: IResourceOperator<GenericCustomResource>?) {
		whenever(definitionOperator.removed(definition))
			.doReturn(true)

		val kind = ResourceKind.create(definition.spec)
		if (resourceOperator != null) {
			whenever(resourceOperator.kind)
				.doReturn(kind)
		}
		if (resourceOperator is INamespacedResourceOperator<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.namespacedOperators[kind] =
				resourceOperator as INamespacedResourceOperator<*, NamespacedKubernetesClient>
		} else if (resourceOperator is INonNamespacedResourceOperator<*, *>) {
			@Suppress("UNCHECKED_CAST")
			context.nonNamespacedOperators[kind] =
				resourceOperator as INonNamespacedResourceOperator<*, NamespacedKubernetesClient>
		}
	}

	private fun setNamespaceForResource(namespace: String?, resource: HasMetadata) {
		whenever(resource.metadata.namespace)
				.doReturn(namespace)
	}

	class TestableKubernetesContext(
		observable: ModelChangeObservable,
		client: NamespacedKubernetesClient,
		private val internalResourceOperators: List<IResourceOperator<out HasMetadata>>,
		private val extensionResourceOperators: List<IResourceOperator<out HasMetadata>>,
		private val customResourcesOperators: Pair<
					INamespacedResourceOperator<GenericCustomResource, NamespacedKubernetesClient>,
					INonNamespacedResourceOperator<GenericCustomResource, NamespacedKubernetesClient>>,
		public override var watch: ResourceWatch,
		override val notification: Notification)
		: KubernetesContext(observable, client, mock()) {

		public override val namespacedOperators
				: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourceOperator<*, NamespacedKubernetesClient>>
			get() {
				return super.namespacedOperators
			}

		public override val nonNamespacedOperators
				: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourceOperator<*, *>>
			get() {
				return super.nonNamespacedOperators
			}

		override fun getInternalResourceOperators(clients: Clients<NamespacedKubernetesClient>)
				: List<IResourceOperator<out HasMetadata>> {
			return internalResourceOperators
		}

		override fun getExtensionResourceOperators(supplier: Clients<NamespacedKubernetesClient>)
				: List<IResourceOperator<out HasMetadata>> {
			return extensionResourceOperators
		}

		public override fun createCustomResourcesOperator(
			definition: CustomResourceDefinition,
			resourceIn: ResourcesIn)
				: IResourceOperator<GenericCustomResource> {
			return when(resourceIn) {
				ResourcesIn.CURRENT_NAMESPACE ->
					customResourcesOperators.first
				ResourcesIn.NO_NAMESPACE,
				ResourcesIn.ANY_NAMESPACE ->
					customResourcesOperators.second
			}
		}

	}
}
