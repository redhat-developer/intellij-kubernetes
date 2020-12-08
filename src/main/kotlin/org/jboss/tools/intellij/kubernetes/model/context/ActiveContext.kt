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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceWatch
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.ANY_NAMESPACE
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.CURRENT_NAMESPACE
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.NO_NAMESPACE
import org.jboss.tools.intellij.kubernetes.model.resource.INamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.INonNamespacedResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.IResourcesProviderFactory
import org.jboss.tools.intellij.kubernetes.model.resource.ResourceKind
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom.GenericResource
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom.NamespacedCustomResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom.NonNamespacedCustomResourcesProvider
import org.jboss.tools.intellij.kubernetes.model.util.Clients
import java.net.URL
import java.util.function.Supplier

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

    enum class ResourcesIn {
        CURRENT_NAMESPACE, ANY_NAMESPACE, NO_NAMESPACE
    }
    val masterUrl: URL
    fun isOpenShift(): Boolean
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R>
    fun getAllResources(definition: CustomResourceDefinition): Collection<GenericResource>
    fun watch(kind: ResourceKind<out HasMetadata>)
    fun watch(definition: CustomResourceDefinition)
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
    fun invalidate(kind: ResourceKind<*>)
    fun replace(resource: HasMetadata): Boolean
    fun close()
}

abstract class ActiveContext<N : HasMetadata, C : KubernetesClient>(
        private val modelChange: IModelChangeObservable,
        client: C,
        context: NamedContext
) : Context(context), IActiveContext<N, C> {

    private val clients = Clients(client)
    override val masterUrl: URL
        get() {
            return clients.get().masterUrl
        }
    private val extensionName: ExtensionPointName<IResourcesProviderFactory<HasMetadata, C, IResourcesProvider<HasMetadata>>> =
            ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.resourceProvider")

    protected open val nonNamespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourcesProvider<*, *>> by lazy {
        getAllResourceProviders(INonNamespacedResourcesProvider::class.java)
    }
    protected open val namespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourcesProvider<out HasMetadata, C>> by lazy {
        val providers = getAllResourceProviders(INamespacedResourcesProvider::class.java)
                as MutableMap<ResourceKind<out HasMetadata>, INamespacedResourcesProvider<out HasMetadata, C>>
        setCurrentNamespace(providers.values)
        providers
    }

    protected open var watch: ResourceWatch = ResourceWatch(
            addOperation = { add(it) },
            removeOperation = { remove(it) },
            replaceOperation = { replace(it) }
    )

    override fun setCurrentNamespace(namespace: String) {
        val currentNamespace = getCurrentNamespace()
        if (namespace == currentNamespace) {
            return
        }
        logger<ActiveContext<*, *>>().debug("Setting current namespace to $namespace.")

        val stopped = stopWatch(currentNamespace)
        clients.get().configuration.namespace = namespace
        setCurrentNamespace(namespace, namespacedProviders.values)
        watchAll(stopped)
        modelChange.fireCurrentNamespace(namespace)
    }

    private fun setCurrentNamespace(providers: Collection<INamespacedResourcesProvider<*, *>>) {
        try {
            val namespacesProvider: INonNamespacedResourcesProvider<N, C> = nonNamespacedProviders[getNamespacesKind()]
                    as INonNamespacedResourcesProvider<N, C>
            val namespace = getCurrentNamespace(namespacesProvider.allResources)
            setCurrentNamespace(namespace, providers)
            watch(namespacesProvider) // always watch namespaces
        } catch (e: KubernetesClientException) {
            logger<ActiveContext<*, *>>().info("Could not set current namespace to all non namespaced providers.", e)
        }
    }

    private fun setCurrentNamespace(namespace: String?, providers: Collection<INamespacedResourcesProvider<*, *>>) {
        if (namespace == null) {
            return
        }
        providers.forEach { it.namespace = namespace }
    }

    override fun getCurrentNamespace(): String? {
        val namespaceKind = getNamespacesKind()
        return getCurrentNamespace(getAllResources(namespaceKind, NO_NAMESPACE))
    }

    private fun getCurrentNamespace(namespaces: Collection<N>): String? {
        var name: String? = clients.get().configuration.namespace
        if (!exists(name, namespaces)) {
            name = null
        }
        return name
    }

    private fun exists(namespace: String?, namespaces: Collection<N>): Boolean {
        return namespace == null
                || namespaces
                .map { it.metadata.name }
                .contains(namespace)
    }

    protected abstract fun getNamespacesKind(): ResourceKind<N>

    override fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R> {
        logger<ActiveContext<*,*>>().debug("Resources $kind requested.")
        synchronized(this) {
            val provider = getProvider(kind, resourcesIn)
            return provider?.allResources
                ?: emptyList()
        }
    }

    private fun setProvider(
            provider: IResourcesProvider<out HasMetadata>,
            kind: ResourceKind<GenericResource>,
            resourcesIn: ResourcesIn
    ) {
        when (resourcesIn) {
            CURRENT_NAMESPACE ->
                namespacedProviders[kind] = provider as INamespacedResourcesProvider<out HasMetadata, C>
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                nonNamespacedProviders[kind] = provider as INonNamespacedResourcesProvider<out HasMetadata, C>
        }
    }

    private fun <R: HasMetadata> getProvider(kind: ResourceKind<R>, resourcesIn: ResourcesIn): IResourcesProvider<R>? {
        return when(resourcesIn) {
            CURRENT_NAMESPACE -> {
                namespacedProviders[kind] as IResourcesProvider<R>?
            }
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                nonNamespacedProviders[kind] as IResourcesProvider<R>?
        }
    }

    private fun getProvider(definition: CustomResourceDefinition): IResourcesProvider<GenericResource> {
        val kind = ResourceKind.create(definition.spec)
        val resourcesIn = toResourcesIn(definition.spec)
        synchronized(this) {
            var provider: IResourcesProvider<GenericResource>? = getProvider(kind, resourcesIn)
            if (provider == null) {
                provider = createCustomResourcesProvider(definition, kind)
            }
            return provider
        }
    }

    override fun getAllResources(definition: CustomResourceDefinition): Collection<GenericResource> {
        return getProvider(definition).allResources
    }

    private fun createCustomResourcesProvider(
            definition: CustomResourceDefinition,
            kind: ResourceKind<GenericResource>)
            : IResourcesProvider<GenericResource> {
        synchronized(this) {
            val resourceIn = toResourcesIn(definition.spec)
            val provider = createCustomResourcesProvider(definition, resourceIn)
            setProvider(provider, kind, resourceIn)
            return provider
        }
    }

    protected open fun createCustomResourcesProvider(
            definition: CustomResourceDefinition,
            resourceIn: ResourcesIn)
            : IResourcesProvider<GenericResource> {
        return when(resourceIn) {
            CURRENT_NAMESPACE ->
                NamespacedCustomResourcesProvider(
                    definition,
                    getCurrentNamespace(),
                    clients.get())
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                NonNamespacedCustomResourcesProvider(
                    definition,
                    clients.get())
        }
    }

    private fun removeCustomResourceProvider(resource: CustomResourceDefinition) {
        val kind = ResourceKind.create(resource.spec)
        watch.ignore(kind)
        val providers = when (toResourcesIn(resource.spec)) {
            CURRENT_NAMESPACE -> namespacedProviders
            ANY_NAMESPACE,
            NO_NAMESPACE -> nonNamespacedProviders
        }
        providers.remove(kind)
    }

    private fun toResourcesIn(spec: CustomResourceDefinitionSpec): ResourcesIn {
        return when (spec.scope) {
            "Cluster" -> NO_NAMESPACE
            "Namespaced" -> CURRENT_NAMESPACE
            else -> throw IllegalArgumentException(
                    "Could not determine scope in spec for custom resource definition ${spec.names.kind}")
        }
    }

    override fun watch(kind: ResourceKind<out HasMetadata>) {
        logger<ActiveContext<*, *>>().debug("Watching $kind resources.")
        watch(namespacedProviders[kind])
        watch(nonNamespacedProviders[kind])
    }

    override fun watch(definition: CustomResourceDefinition) {
        watch(getProvider(definition))
    }

    private fun watchAll(kinds: Collection<ResourceKind<out HasMetadata>>) {
        val watchables = namespacedProviders.entries.toList()
            .filter { kinds.contains(it.key) }
            .map { Pair(it.value.kind, it.value.getWatchable()) }
        watch.watchAll(watchables
                as Collection<Pair<ResourceKind<out HasMetadata>, Supplier<Watchable<Watch, Watcher<in HasMetadata>>?>>>)
    }

    private fun watch(provider: IResourcesProvider<*>?) {
        if (provider == null) {
            return
        }

        watch.watch(provider.kind, provider.getWatchable()
                as Supplier<Watchable<Watch, Watcher<in HasMetadata>>?>)
    }

    override fun add(resource: HasMetadata): Boolean {
        val added = when (resource) {
            is CustomResourceDefinition ->
                addResource(resource)
            else ->
                addResource(resource)
        }
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    private fun addResource(resource: HasMetadata): Boolean {
        // we need to add resource to both providers (ex. all pods & only namespaced pods)
        val kind = ResourceKind.create(resource)
        val addedToNonNamespaced = addResource(resource, nonNamespacedProviders[kind])
        val addedToNamespaced = getCurrentNamespace() == resource.metadata.namespace
                && addResource(resource, namespacedProviders[kind])
        return addedToNonNamespaced ||
                addedToNamespaced
    }

    private fun addResource(resource: CustomResourceDefinition): Boolean {
        val added = addResource(resource as HasMetadata)
        if (added) {
            createCustomResourcesProvider(resource, ResourceKind.create(resource.spec))
        }
        return added
    }

    private fun addResource(resource: HasMetadata, provider: IResourcesProvider<out HasMetadata>?): Boolean {
        if (provider == null) {
            return false
        }
        return provider.add(resource)
    }

    override fun remove(resource: HasMetadata): Boolean {
        val removed = if (resource is CustomResourceDefinition) {
            removeResource(resource)
        } else {
            removeResource(resource)
        }
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    private fun removeResource(resource: HasMetadata): Boolean {
        val kind = ResourceKind.create(resource)
        // we need to remove resource from both providers
        val removedNonNamespaced = removeResource(resource, nonNamespacedProviders[kind])
        val removedNamespaced = (getCurrentNamespace() == resource.metadata.namespace) &&
                removeResource(resource, namespacedProviders[kind])
        return removedNonNamespaced
                || removedNamespaced
    }

    private fun removeResource(definition: CustomResourceDefinition): Boolean {
        val removed = removeResource(definition as HasMetadata)
        if (removed) {
            removeCustomResourceProvider(definition)
        }
        return removed
    }

    private fun removeResource(resource: HasMetadata, provider: IResourcesProvider<out HasMetadata>?): Boolean {
        if (provider == null) {
            return false
        }
        return provider.remove(resource)
    }

    override fun invalidate() {
        logger<ActiveContext<*, *>>().debug("Invalidating all providers.")
        namespacedProviders.values.forEach { it.invalidate() }
        nonNamespacedProviders.values.forEach { it.invalidate() }
        modelChange.fireModified(this)
    }

    override fun replace(resource: HasMetadata): Boolean {
        val replaced = when(resource) {
            is CustomResourceDefinition ->
                replace(ResourceKind.create(resource.spec), resource)
            else ->
                replace(ResourceKind.create(resource), resource)
        }
        if (replaced) {
            modelChange.fireModified(resource)
        }
        return replaced
    }

    private fun replace(kind: ResourceKind<out HasMetadata>, resource: HasMetadata): Boolean {
        val replaceNamespaced = namespacedProviders[kind]?.replace(resource) ?: false
        val replaceNonNamespaced = nonNamespacedProviders[kind]?.replace(resource) ?: false
        return replaceNamespaced
                || replaceNonNamespaced
    }

    override fun invalidate(kind: ResourceKind<*>) {
        logger<ActiveContext<*, *>>().debug("Invalidating resource providers for $kind resources.")
        namespacedProviders[kind]?.invalidate()
        nonNamespacedProviders[kind]?.invalidate()
        modelChange.fireModified(kind)
    }

    /**
     * Stops watching all watchables for the given namespace. Doesn't stop the cluster wide watchables nor
     * the ones for a different namespace.
     *
     * @param namespace the namespace that the watchables should be stopped for
     */
    private fun stopWatch(namespace: String?): Collection<ResourceKind<out HasMetadata>> {
        logger<ActiveContext<*, *>>().debug("Stopping all watches for namespace $namespace.")
        return watch.ignoreAll(namespacedProviders(namespace).map { it.kind })
    }

    protected open fun namespacedProviders(namespace: String?): List<INamespacedResourcesProvider<out HasMetadata, C>> {
        return namespacedProviders.values
                .filter { it.namespace == namespace }
    }

    override fun close() {
        logger<ActiveContext<*, *>>().debug("Closing context ${context.name}.")
        watch.close()
        clients.close()
    }

    private fun <P: IResourcesProvider<out HasMetadata>> getAllResourceProviders(type: Class<P>)
            : MutableMap<ResourceKind<out HasMetadata>, P> {
        val providers = mutableMapOf<ResourceKind<out HasMetadata>, P>()
        providers.putAll(
                getInternalResourceProviders(clients)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        providers.putAll(
                getExtensionResourceProviders(clients)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        return providers
    }

    protected abstract fun getInternalResourceProviders(supplier: Clients<C>): List<IResourcesProvider<out HasMetadata>>

    protected open fun getExtensionResourceProviders(supplier: Clients<C>): List<IResourcesProvider<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(supplier) }
    }
}
