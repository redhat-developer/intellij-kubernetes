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
package org.jboss.tools.intellij.kubernetes.model.context

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
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
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.ModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.namespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.nonNamespacedResourceProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.AllPodsProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NamespacesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.NodesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.ServicesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom.GenericResource

import org.junit.Before
import org.junit.Test

class KubernetesContextTest {

	private val allNamespaces = arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)
	private val currentNamespace = NAMESPACE2
	private val client: NamespacedKubernetesClient = client(currentNamespace.metadata.name, allNamespaces)
	private val namespacedDefinition = customResourceDefinition(
			"namespaced crd","version1", "group1", "namespaced-crd", "Namespaced")
	private val clusterwideDefinition = customResourceDefinition(
			"cluster crd", "version2", "group2", "cluster-crd", "Cluster")
	private val customResourceDefinitionsProvider: INonNamespacedResourcesProvider<CustomResourceDefinition> =
			nonNamespacedResourceProvider(
					ResourceKind.new(CustomResourceDefinition::class.java),
					setOf(namespacedDefinition, clusterwideDefinition))

	private val observable: ModelChangeObservable = mock()

	private val namespacesProvider = nonNamespacedResourceProvider(
			NamespacesProvider.KIND,
			allNamespaces.toSet())
	private val nodesProvider = nonNamespacedResourceProvider(
			NodesProvider.KIND,
			listOf(resource("node1"),
					resource("node2"),
					resource("node3")))
	private val allPods = arrayOf(POD1, POD2, POD3)
	private val namespacedPodsProvider = namespacedResourceProvider(
			AllPodsProvider.KIND,
			allPods.toList(),
			currentNamespace)
	private val allPodsProvider = nonNamespacedResourceProvider(
			AllPodsProvider.KIND,
			allPods.toList())

	private val hasMetadata1 = resource<HasMetadata>("hasMetadata1")
	private val hasMetadata2 = resource<HasMetadata>("hasMetadata2")
	private val hasMetadataProvider: INamespacedResourcesProvider<HasMetadata> = namespacedResourceProvider(
			ResourceKind.new(HasMetadata::class.java),
			setOf(hasMetadata1, hasMetadata2),
			currentNamespace)

	private val namespacedCustomResource1 = resource<GenericResource>("genericCustomResource1")
	private val namespacedCustomResource2 = resource<GenericResource>("genericCustomResource2")
	private val watchable1: Watchable<Watch, Watcher<GenericResource>> = mock()
	private val watchableSupplier1: () -> Watchable<Watch, Watcher<GenericResource>>? = { watchable1 }
	private val namespacedResourcesProvider: INamespacedResourcesProvider<GenericResource> =
			namespacedResourceProvider(
					ResourceKind.new(GenericResource::class.java),
					setOf(namespacedCustomResource1, namespacedCustomResource2),
					currentNamespace,
					watchableSupplier1
			)
	private val nonNamespacedCustomResource1 = resource<GenericResource>(
			"genericCustomResource1", "smurfington")
	private val watchable2: Watchable<Watch, Watcher<GenericResource>> = mock()
	private val watchableSupplier2: () -> Watchable<Watch, Watcher<GenericResource>>? = { watchable2 }
	private val nonNamespacedResourcesProvider: INonNamespacedResourcesProvider<GenericResource> =
			nonNamespacedResourceProvider(
					ResourceKind.new(GenericResource::class.java),
					setOf(nonNamespacedCustomResource1),
					watchableSupplier2
			)

	private val watchable3: Watchable<Watch, Watcher<in HasMetadata>> = mock()
	private val watchableSupplier3: () -> Watchable<Watch, Watcher<in HasMetadata>>? = { watchable3 }

	val allWatchables = listOf(
			watchableSupplier1,
			watchableSupplier2,
			watchableSupplier3) as List<() -> Watchable<Watch, Watcher<in HasMetadata>>?>
	val namespacedWatchables = listOf(
			watchableSupplier1,
			watchableSupplier3) as List<() -> Watchable<Watch, Watcher<in HasMetadata>>>

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
		val context = spy(TestableKubernetesContext(
				observable,
				this@KubernetesContextTest.client,
				internalResourcesProviders,
				extensionResourceProviders,
				Pair(namespacedResourcesProvider, nonNamespacedResourcesProvider),
				mock()
		))
		doReturn(allWatchables)
				.whenever(context).getAllWatchables()
		doReturn(namespacedWatchables)
				.whenever(context).getWatchables(any())

		return context
	}

	@Test
	fun `context instantiation should retrieve current namespace in client`() {
		// given
		// context created in #before
		// when
		// then
		verify(client.configuration).namespace
	}

	@Test
	fun `#setCurrentNamespace should remove watched resources for current namespace`() {
		// given
		val captor =
				argumentCaptor<List<() -> Watchable<Watch, Watcher<in HasMetadata>>>>()
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).ignoreAll(captor.capture())
		assertThat(captor.firstValue).containsOnly(*namespacedWatchables.toTypedArray())
	}

	@Test
	fun `#setCurrentNamespace should watch new watchables for new current namespace`() {
		// given
		val captor =
				argumentCaptor<List<() -> Watchable<Watch, Watcher<in HasMetadata>>?>>()
		// when
		context.setCurrentNamespace(NAMESPACE1.metadata.name)
		// then
		verify(context.watch).watchAll(captor.capture())
		assertThat(captor.firstValue).containsOnly(*namespacedWatchables.toTypedArray())
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
		verify(observable).fireCurrentNamespace(NAMESPACE1.metadata.name)
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
	fun `#getResources should get all resources in provider for given resource type in correct ResourcesIn type`() {
		// given
		// when
		context.getResources(NodesProvider.KIND, ResourcesIn.NO_NAMESPACE)
		// then
		verify(nodesProvider).getAllResources()
	}

	@Test
	fun `#getResources should not get all resources in provider for given resource type in wrong ResourcesIn type`() {
		// given
		// when
		// there are no namespaces in current namespace
		context.getResources(NodesProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(nodesProvider, never()).getAllResources()
	}

	@Test
	fun `#getResources should return empty list if there's no provider for given resource type in given ResourceIn type`() {
		// given
		// when
		// namespace provider exists but for ResourceIn.NO_NAMESPACE
		context.getResources(NodesProvider.KIND, ResourcesIn.CURRENT_NAMESPACE)
		// then
		verify(nodesProvider, never()).getAllResources()
	}

	@Test
	fun `#getResources should return empty list if there's no provider of given resource type in given ResourceIn type`() {
		// given
		// when
		val services = context.getResources(ServicesProvider.KIND, ResourcesIn.NO_NAMESPACE)
		// then
		assertThat(services).isEmpty()
	}

	@Test
	fun `#getCustomResources should query CustomResourceProvider`() {
		// given
		// when
		context.getCustomResources(namespacedDefinition)
		// then
		verify(namespacedResourcesProvider).getAllResources()
	}

	@Test
	fun `#getCustomResources should create CustomResourceProvider once and reuse it`() {
		// given
		// when
		context.getCustomResources(namespacedDefinition)
		context.getCustomResources(namespacedDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(eq(namespacedDefinition), any(), any())
	}

	@Test
	fun `#getCustomResources should create CustomResourceProvider for each unique definition`() {
		// given
		// when
		context.getCustomResources(namespacedDefinition)
		context.getCustomResources(clusterwideDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(eq(namespacedDefinition), any(), any())
		verify(context, times(1)).createCustomResourcesProvider(eq(clusterwideDefinition), any(), any())
	}

	@Test
	fun `#getCustomResources should watch watchable in new resources provider`() {
		// given
		// when
		context.getCustomResources(namespacedDefinition)
		// then
		verify(context, times(1)).createCustomResourcesProvider(eq(namespacedDefinition), any(), any())
		verify(namespacedResourcesProvider, times(1)).getWatchable()
		verify(context.watch, times(1)).watch(watchableSupplier1 as () -> Watchable<Watch, Watcher<in HasMetadata>>?)
	}

	@Test
	fun `#getCustomResources should only watch watchable when creating provider, not when reusing existing`() {
		// given
		// when
		context.getCustomResources(namespacedDefinition)
		context.getCustomResources(namespacedDefinition)
		// then
		verify(context.watch, times(1)).watch(watchableSupplier1 as () -> Watchable<Watch, Watcher<in HasMetadata>>?)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `#getCustomResources should throw if scope is unknown`() {
		// given
		val bogusScope = customResourceDefinition(
				"bogus scope","version1", "group1", "bogus-scope-crd", "Bogus")
		// when
		context.getCustomResources(bogusScope)
		// then
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
		doReturn(true)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		val added = context.add(pod)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(namespace) should return true if namespace was added to namespace provider`() {
		// given
		val namespace = resource<Namespace>("pod")
		doReturn(true)
				.whenever(namespacesProvider).add(namespace)
		// when
		val added = context.add(namespace)
		// then
		assertThat(added).isTrue()
	}

	@Test
	fun `#add(pod) should return false if pod was not added to pods provider`() {
		// given
		val pod = resource<Pod>("pod")
		doReturn(false)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		val added = context.add(pod)
		// then
		assertThat(added).isFalse()
	}

	@Test
	fun `#add(pod) should fire if provider added pod`() {
		// given
		val pod = resource<Pod>("gargamel", currentNamespace.metadata.name)
		doReturn(true)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		context.add(pod)
		// then
		verify(observable).fireAdded(pod)
	}

	@Test
	fun `#add(pod) should not fire if provider did not add pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		doReturn(false)
				.whenever(namespacedPodsProvider).add(pod)
		// when
		context.add(pod)
		// then
		verify(observable, never()).fireAdded(pod)
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
						eq(currentNamespace),
						eq(ResourcesIn.CURRENT_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should create clusterwide custom resources provider if definition was added`() {
		// given
		val currentNamespace = currentNamespace.metadata.name
		whenever(customResourceDefinitionsProvider.add(clusterwideDefinition))
				.doReturn(true)
		// when
		context.add(clusterwideDefinition)
		// then
		verify(context, times(1))
				.createCustomResourcesProvider(
						eq(clusterwideDefinition),
						eq(currentNamespace),
						eq(ResourcesIn.NO_NAMESPACE))
	}

	@Test
	fun `#add(CustomResourceDefinition) should NOT create custom resources provider if definition was NOT added`() {
		// given
		whenever(namespacedResourcesProvider.add(clusterwideDefinition))
				.doReturn(false)
		// when
		context.add(clusterwideDefinition)
		// then
		verify(context, never()).createCustomResourcesProvider(eq(clusterwideDefinition), any(), any())
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
		doReturn(true)
				.whenever(namespacedPodsProvider).remove(pod)
		// when
		context.remove(pod)
		// then
		verify(observable).fireRemoved(pod)
	}

	@Test
	fun `#remove(pod) should not fire if provider did not remove pod`() {
		// given
		val pod = resource<Pod>("gargamel")
		doReturn(false)
				.whenever(namespacedPodsProvider).remove(pod)
		// when
		context.remove(pod)
		// then
		verify(observable, never()).fireRemoved(pod)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove clusterwide custom resource provider when definition is removed`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				nonNamespacedResourcesProvider)
		// when
		context.remove(clusterwideDefinition)
		// then
		assertThat(context.nonNamespacedProviders)
				.doesNotContainValue(nonNamespacedResourcesProvider)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should remove namespaced custom resource provider when definition is removed`() {
		// given
		givenCustomResourceProvider(namespacedDefinition,
				customResourceDefinitionsProvider,
				namespacedResourcesProvider)
		// when
		context.remove(namespacedDefinition)
		// then
		assertThat(context.namespacedProviders)
				.doesNotContainValue(namespacedResourcesProvider)
	}

	@Test
	fun `#remove(CustomResourceDefinition) should NOT remove namespaced custom resource provider when definition is NOT removed`() {
		// given
		givenCustomResourceProvider(clusterwideDefinition,
				customResourceDefinitionsProvider,
				namespacedResourcesProvider)
		whenever(customResourceDefinitionsProvider.remove(clusterwideDefinition))
				.doReturn(false)
		// when
		context.remove(clusterwideDefinition)
		// then
		assertThat(context.namespacedProviders)
				.containsValue(namespacedResourcesProvider)
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
	fun `#invalidate(resource) should invalidate resource provider`() {
		// given
		val pod = resource<Pod>("pod", NAMESPACE2.metadata.name)
		// when
		context.invalidate(pod)
		// then
		verify(namespacesProvider, never()).invalidate(any())
		verify(namespacedPodsProvider).invalidate()
	}

	@Test
	fun `#close should close client`() {
		// given
		// when
		context.close()
		// then
		verify(client).close()
	}

	@Test
	fun `#startWatch() should start watch for all providers`() {
		// given
		val providersCaptor =
				argumentCaptor<Collection<() -> Watchable<Watch, Watcher<in HasMetadata>>?>>()
		// when
		context.startWatch()
		// then
		verify(context.watch).watchAll(providersCaptor.capture())
		assertThat(providersCaptor.firstValue).containsOnly(*allWatchables.toTypedArray())
	}

	private fun givenCustomResourceProvider(
			definition: CustomResourceDefinition,
			definitionProvider: IResourcesProvider<CustomResourceDefinition>,
			resourceProvider: IResourcesProvider<GenericResource>) {
		whenever(definitionProvider.remove(definition))
				.doReturn(true)
		val kind = ResourceKind.new(definition.spec)
		if (resourceProvider is INamespacedResourcesProvider<*>) {
			context.namespacedProviders[kind] = resourceProvider
		} else if (resourceProvider is INonNamespacedResourcesProvider<*>) {
			context.nonNamespacedProviders[kind] = resourceProvider
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
					INamespacedResourcesProvider<GenericResource>,
					INonNamespacedResourcesProvider<GenericResource>>,
			public override var watch: ResourceWatch)
		: KubernetesContext(observable, client, mock()) {

		public override val namespacedProviders: MutableMap<ResourceKind<out HasMetadata>,
				INamespacedResourcesProvider<out HasMetadata>>
			get() {
				return super.namespacedProviders
			}

		public override val nonNamespacedProviders: MutableMap<ResourceKind<out HasMetadata>,
				INonNamespacedResourcesProvider<out HasMetadata>>
			get() {
				return super.nonNamespacedProviders
			}

		public override fun getWatchables(namespace: String): Collection<() -> Watchable<Watch, Watcher<in HasMetadata>>?> {
			return super.getWatchables(namespace)
		}

		public override fun getAllWatchables(): Collection<() -> Watchable<Watch, Watcher<in HasMetadata>>?> {
			return super.getAllWatchables()
		}

		override fun getInternalResourceProviders(client: NamespacedKubernetesClient)
				: List<IResourcesProvider<out HasMetadata>> {
			return internalResourceProviders
		}

		override fun getExtensionResourceProviders(client: NamespacedKubernetesClient)
				: List<IResourcesProvider<out HasMetadata>> {
			return extensionResourcesProviders
		}

		public override fun createCustomResourcesProvider(
				definition: CustomResourceDefinition,
				namespace: String?,
				resourceIn: ResourcesIn)
				: IResourcesProvider<GenericResource> {
			return when(resourceIn) {
				ResourcesIn.CURRENT_NAMESPACE ->
					customResourcesProviders.first
				ResourcesIn.NO_NAMESPACE,
				ResourcesIn.ANY_NAMESPACE ->
					customResourcesProviders.second
			}
		}

		fun getNonNamespacedWatchables(): List<() -> Watchable<Watch, Watcher<in HasMetadata>>> {
			return nonNamespacedProviders.values.map { it.getWatchable() as () -> Watchable<Watch, Watcher<in HasMetadata>> }
		}

		fun getNamespacedWatchables(): List<() -> Watchable<Watch, Watcher<in HasMetadata>>> {
			return namespacedProviders.values.map { it.getWatchable() as () -> Watchable<Watch, Watcher<in HasMetadata>> }
		}

	}
}