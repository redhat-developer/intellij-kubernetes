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
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import org.jboss.tools.intellij.kubernetes.model.IModelChangeObservable
import org.jboss.tools.intellij.kubernetes.model.ResourceException
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

interface IActiveContext<N: HasMetadata, C: KubernetesClient>: IContext {

    enum class ResourcesIn {
        CURRENT_NAMESPACE, ANY_NAMESPACE, NO_NAMESPACE
    }

    val client: C
    fun isOpenShift(): Boolean
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> getResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R>
    fun getCustomResources(definition: CustomResourceDefinition): Collection<GenericResource>
    fun add(resource: HasMetadata): Boolean
    fun remove(resource: HasMetadata): Boolean
    fun invalidate(kind: Any)
    fun startWatch()
    fun close()
}

abstract class ActiveContext<N : HasMetadata, C : KubernetesClient>(
        private val modelChange: IModelChangeObservable,
        final override val client: C,
        context: NamedContext
) : Context(context), IActiveContext<N, C> {

    private val extensionName: ExtensionPointName<IResourcesProviderFactory<HasMetadata, C, IResourcesProvider<HasMetadata>>> =
            ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.resourceProvider")
    protected open val namespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourcesProvider<out HasMetadata>> by lazy {
        val providers = getAllResourceProviders(INamespacedResourcesProvider::class.java)
        val namespace = getCurrentNamespace()
        providers.forEach { it.value.namespace = namespace }
        providers
    }
    protected open val nonNamespacedProviders: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourcesProvider<out HasMetadata>> by lazy {
        getAllResourceProviders(INonNamespacedResourcesProvider::class.java)
    }
    protected var namespace: String? = client.configuration.namespace
    protected open var watch: ResourceWatch = ResourceWatch(
            addOperation = { add(it) },
            removeOperation = { remove(it) })

    override fun setCurrentNamespace(namespace: String) {
        val currentNamespace = getCurrentNamespace()
        if (namespace == currentNamespace) {
            return
        }

        if (currentNamespace != null) {
            stopWatch(currentNamespace)
        }
        client.configuration.namespace = namespace
        namespacedProviders.forEach {
            it.value.invalidate()
            it.value.namespace = namespace
        }
        startWatch(namespace)
        modelChange.fireCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        var name: String? = client.configuration.namespace
        if (name == null) {
            try {
                name = getNamespaces().firstOrNull()?.metadata?.name
            } catch (e: KubernetesClientException) {
                logger<ActiveContext<N, C>>().warn("Could not determine current namespace: loading all namespaces failed.", e)
            }
        }
        return name
    }

    protected abstract fun getNamespaces(): Collection<N>

    override fun <R: HasMetadata> getResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R> {
        val provider = getProvider(kind, resourcesIn)
        return provider?.getAllResources() ?: return emptyList()
    }

    private fun setProvider(
            provider: IResourcesProvider<out HasMetadata>,
            kind: ResourceKind<GenericResource>,
            resourcesIn: ResourcesIn
    ) {
        when (resourcesIn) {
            CURRENT_NAMESPACE ->
                namespacedProviders[kind] = provider as INamespacedResourcesProvider<out HasMetadata>
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                nonNamespacedProviders[kind] = provider as INonNamespacedResourcesProvider<out HasMetadata>
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

    override fun getCustomResources(definition: CustomResourceDefinition)
            : Collection<GenericResource> {
        val kind = ResourceKind.new(definition.spec)
        val resourcesIn = toResourcesIn(definition.spec)
        val provider: IResourcesProvider<GenericResource> = getProvider(kind, resourcesIn)
                ?: createCustomResourcesProvider(definition, kind)
        return provider.getAllResources()
    }

    private fun createCustomResourcesProvider(
            definition: CustomResourceDefinition,
            kind: ResourceKind<GenericResource>)
            : IResourcesProvider<GenericResource> {
        val resourceIn = toResourcesIn(definition.spec)
        val provider = createCustomResourcesProvider(definition, getCurrentNamespace(), resourceIn)
        val watchable = provider.getWatchable() as () -> Watchable<Watch, Watcher<HasMetadata>>?
        watch.watch(watchable)
        setProvider(provider, kind, resourceIn)
        return provider
    }

    protected open fun createCustomResourcesProvider(
            definition: CustomResourceDefinition,
            namespace: String?,
            resourceIn: ResourcesIn)
            : IResourcesProvider<GenericResource> {
        return when(resourceIn) {
            CURRENT_NAMESPACE ->
                NamespacedCustomResourcesProvider(definition, namespace, client)
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                NonNamespacedCustomResourcesProvider(definition, client)
        }
    }

    private fun removeCustomResourceProvider(resource: CustomResourceDefinition) {
        val resourcesIn = toResourcesIn(resource.spec)
        val kind = ResourceKind.new(resource.spec)
        val providers = when (resourcesIn) {
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

    override fun add(resource: HasMetadata): Boolean {
        return when (resource) {
            is CustomResourceDefinition ->
                addResource(resource)
            else ->
                addResource(resource)
        }
    }

    private fun addResource(resource: HasMetadata): Boolean {
        // we need to add resource to both providers (ex. all pods & only namespaced pods)
        val addedToNonNamespaced = add(resource, nonNamespacedProviders[ResourceKind.new(resource)])
        val addedToNamespaced = (getCurrentNamespace() == resource.metadata?.namespace) &&
                add(resource, namespacedProviders[ResourceKind.new(resource)])
        return addedToNonNamespaced.or(
                addedToNamespaced)
    }

    private fun addResource(resource: CustomResourceDefinition): Boolean {
        val added = addResource(resource as HasMetadata)
        if (added) {
            createCustomResourcesProvider(resource, ResourceKind.new(resource.spec))
        }
        return added
    }

    private fun add(resource: HasMetadata, provider: IResourcesProvider<out HasMetadata>?): Boolean {
        if (provider == null) {
            return false
        }
        val added = provider.add(resource)
        if (added) {
            modelChange.fireAdded(resource)
        }
        return added
    }

    override fun remove(resource: HasMetadata): Boolean {
        return if (resource is CustomResourceDefinition) {
            removeResource(resource)
        } else {
            removeResource(resource)
        }
    }

    private fun removeResource(resource: HasMetadata): Boolean {
        val kind = ResourceKind.new(resource)
        // we need to remove resource from both providers
        val removedNonNamespaced = removeResource(resource, nonNamespacedProviders[kind])
        val removedNamespaced = (getCurrentNamespace() == resource.metadata.namespace) &&
                removeResource(resource, namespacedProviders[kind])
        return removedNonNamespaced.or(removedNamespaced)
    }

    private fun removeResource(resource: CustomResourceDefinition): Boolean {
        val removed = removeResource(resource as HasMetadata)
        if (removed) {
            removeCustomResourceProvider(resource)
        }
        return removed
    }

    private fun removeResource(resource: HasMetadata, provider: IResourcesProvider<out HasMetadata>?): Boolean {
        if (provider == null) {
            return false
        }
        val removed = provider.remove(resource)
        if (removed) {
            modelChange.fireRemoved(resource)
        }
        return removed
    }

    override fun invalidate() {
        namespacedProviders.values.forEach { it.invalidate() }
        nonNamespacedProviders.values.forEach { it.invalidate() }
    }

    override fun invalidate(kind: Any) {
        when(kind) {
            is ResourceKind<*> ->
                invalidate(kind)
            is HasMetadata ->
                invalidate(kind)
            is CustomResourceDefinition ->
                invalidate(kind)
        }
    }

    private fun invalidate(resource: HasMetadata) {
        invalidate(ResourceKind.new(resource))
    }

    private fun invalidate(definition: CustomResourceDefinition) {
        invalidate(ResourceKind.new(definition.spec))
    }

    private fun invalidate(kind: ResourceKind<*>) {
        namespacedProviders[kind]?.invalidate()
        nonNamespacedProviders[kind]?.invalidate()
    }

    override fun startWatch() {
        val namespace = getCurrentNamespace() ?: return
        startWatch(namespace)
    }

    private fun startWatch(namespace: String) {
        try {
            watch.watchAll(getRetrieveOperations(namespace))
        } catch (e: ResourceException) {
            logger<ActiveContext<N, C>>().warn("Could not start watching resources on server ${client.masterUrl}", e)
        }
    }

    private fun stopWatch(namespace: String) {
        watch.ignoreAll(getRetrieveOperations(namespace))
    }

    protected open fun getRetrieveOperations(namespace: String)
            : Collection<() -> Watchable<Watch, Watcher<HasMetadata>>?> {
        val resources: MutableList<() ->Watchable<Watch, Watcher<HasMetadata>>?> = mutableListOf()
        resources.addAll(namespacedProviders.values
                .map { it.getWatchable() as () -> Watchable<Watch, Watcher<HasMetadata>>? })
        resources.addAll(nonNamespacedProviders.values
                .map { it.getWatchable() as () -> Watchable<Watch, Watcher<HasMetadata>>? })
        return resources
    }

    override fun close() {
        client.close()
    }

    private fun <P: IResourcesProvider<out HasMetadata>> getAllResourceProviders(type: Class<P>)
            : MutableMap<ResourceKind<out HasMetadata>, P> {
        val providers = mutableMapOf<ResourceKind<out HasMetadata>, P>()
        providers.putAll(
                getInternalResourceProviders(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        providers.putAll(
                getExtensionResourceProviders(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        return providers
    }

    protected abstract fun getInternalResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>>

    protected open fun getExtensionResourceProviders(client: C): List<IResourcesProvider<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(client) }
    }
}
