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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.redhat.devtools.intellij.common.kubernetes.ClusterHelper
import com.redhat.devtools.intellij.common.kubernetes.ClusterInfo
import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.WatchListeners
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.ANY_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.CURRENT_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.NO_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.dashboard.IDashboard
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.NonCachingSingleResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionContextFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.NamespacedCustomResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.NonNamespacedCustomResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isNotFound
import com.redhat.devtools.intellij.kubernetes.model.util.setWillBeDeleted
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.model.Scope
import java.net.URL

abstract class ActiveContext<N : HasMetadata, C : KubernetesClient>(
    context: NamedContext,
    private val modelChange: IResourceModelObservable,
    val client: ClientAdapter<out C>,
    protected open val dashboard: IDashboard,
    private var singleResourceOperator: NonCachingSingleResourceOperator = NonCachingSingleResourceOperator(client),
) : Context(context), IActiveContext<N, C> {

    companion object {
        private const val DEFAULT_NAMESPACE = "default"
    }

    override val active: Boolean = true
    override val masterUrl: URL
        get() {
            return client.get().masterUrl
        }

    override val version: ClusterInfo by lazy {
        ClusterHelper.getClusterInfo(client.get())
    }

    protected abstract val namespaceKind : ResourceKind<N>

    private val extensionName: ExtensionPointName<IResourceOperatorFactory<HasMetadata, KubernetesClient, IResourceOperator<HasMetadata>>> =
            ExtensionPointName("com.redhat.devtools.intellij.kubernetes.resourceOperators")

    protected open val nonNamespacedOperators: MutableMap<ResourceKind<out HasMetadata>, INonNamespacedResourceOperator<*, *>> by lazy {
        getAllResourceOperators(INonNamespacedResourceOperator::class.java)
    }

    protected open val namespacedOperators: MutableMap<ResourceKind<out HasMetadata>, INamespacedResourceOperator<out HasMetadata, C>> by lazy {
        @Suppress("UNCHECKED_CAST")
        val operators = getAllResourceOperators(INamespacedResourceOperator::class.java)
                as MutableMap<ResourceKind<out HasMetadata>, INamespacedResourceOperator<out HasMetadata, C>>
        setCurrentNamespace(operators.values)
        operators
    }

    protected open var watch = ResourceWatch<ResourceKind<out HasMetadata>>()
    protected open val watchListener = WatchListeners({ added(it) }, { removed(it) }, { replaced(it) })

    protected open val notification: Notification = Notification()

    private fun setCurrentNamespace(operators: Collection<INamespacedResourceOperator<*, *>>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val namespacesOperator: INonNamespacedResourceOperator<N, C> =
                nonNamespacedOperators[namespaceKind] as INonNamespacedResourceOperator<N, C>
            val namespace = getCurrentNamespace()
            setCurrentNamespace(namespace, operators)
            watch(namespacesOperator) // always watch namespaces
        } catch (e: KubernetesClientException) {
            logger<ActiveContext<*, *>>().info("Could not set current namespace to all non namespaced operators.", e)
        }
    }

    private fun setCurrentNamespace(namespace: String?, operators: Collection<INamespacedResourceOperator<*, *>>) {
        if (namespace == null) {
            return
        }
        operators.forEach { it.namespace = namespace }
    }

    override fun getCurrentNamespace(): String? {
        val current = client.namespace
        return if (!current.isNullOrEmpty()) {
            current
        } else {
            return try {
                val allNamespaces = getAllResources(namespaceKind, NO_NAMESPACE)
                val namespace =
                    allNamespaces.find { namespace: HasMetadata -> DEFAULT_NAMESPACE == namespace.metadata.name }
                        ?: allNamespaces.firstOrNull()
                namespace?.metadata?.name
            } catch (e: ResourceException) {
                logger<ActiveContext<*,*>>().warn("Could not list all namespaces to use 1st as current namespace.", e)
                null
            }
        }
    }

    override fun isCurrentNamespace(resource: HasMetadata): Boolean {
        return getCurrentNamespace() == resource.metadata?.name
    }

    override fun isCurrentNamespace(namespace: String): Boolean {
        return namespace == getCurrentNamespace()
    }

    override fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R> {
        logger<ActiveContext<*, *>>().debug("Resources $kind requested.")
        return try {
            synchronized(this) {
                val operator = getOperator(kind, resourcesIn)
                operator?.allResources
                    ?: emptyList()
            }
        } catch (e: KubernetesClientException) {
            if (e.isNotFound()) {
                emptyList()
            } else {
                throw ResourceException("Could not get ${kind.kind}s for server $masterUrl", e)
            }
        }
    }

    private fun setOperator(
        operator: IResourceOperator<out HasMetadata>,
        kind: ResourceKind<GenericKubernetesResource>,
        resourcesIn: ResourcesIn
    ) {
        when (resourcesIn) {
            CURRENT_NAMESPACE ->
                @Suppress("UNCHECKED_CAST")
                namespacedOperators[kind] = operator as INamespacedResourceOperator<out HasMetadata, C>
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                @Suppress("UNCHECKED_CAST")
                nonNamespacedOperators[kind] = operator as INonNamespacedResourceOperator<out HasMetadata, C>
        }
    }

    private fun <R: HasMetadata> getOperator(kind: ResourceKind<R>, resourcesIn: ResourcesIn): IResourceOperator<R>? {
        return when(resourcesIn) {
            CURRENT_NAMESPACE -> {
                @Suppress("UNCHECKED_CAST")
                namespacedOperators[kind] as IResourceOperator<R>?
            }
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                @Suppress("UNCHECKED_CAST")
                nonNamespacedOperators[kind] as IResourceOperator<R>?
        }
    }

    private fun getOperator(definition: CustomResourceDefinition): IResourceOperator<GenericKubernetesResource>? {
        val kind = ResourceKind.create(definition.spec) ?: return null
        val resourcesIn = toResourcesIn(definition.spec)
        synchronized(this) {
            var operator: IResourceOperator<GenericKubernetesResource>? = getOperator(kind, resourcesIn)
            if (operator == null) {
                operator = createCustomResourcesOperator(definition, kind)
            }
            return operator
        }
    }

    private fun createCustomResourcesOperator(
        definition: CustomResourceDefinition,
        kind: ResourceKind<GenericKubernetesResource>)
            : IResourceOperator<GenericKubernetesResource>? {
        synchronized(this) {
            val resourceIn = toResourcesIn(definition.spec)
            val operator = createCustomResourcesOperator(definition, resourceIn) ?: return null
            setOperator(operator, kind, resourceIn)
            return operator
        }
    }

    override fun getAllResources(definition: CustomResourceDefinition): Collection<GenericKubernetesResource> {
        logger<ActiveContext<*, *>>().debug("Getting all ${definition.metadata.name} resources.")
        return try {
            getOperator(definition)?.allResources ?: return emptyList()
        } catch (e: IllegalArgumentException) {
            throw ResourceException("Could not get custom resources for ${definition.metadata}: $e", e)
        }
    }

    protected open fun createCustomResourcesOperator(
            definition: CustomResourceDefinition,
            resourceIn: ResourcesIn)
            : IResourceOperator<GenericKubernetesResource>? {
        val kind = ResourceKind.create(definition.spec) ?: return null
        val context = CustomResourceDefinitionContextFactory.create(definition)
        return when(resourceIn) {
            CURRENT_NAMESPACE ->
                NamespacedCustomResourceOperator(
                    kind,
                    context,
                    getCurrentNamespace(),
                    client.get()
                )
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                NonNamespacedCustomResourceOperator(
                    kind,
                    context,
                    client.get())
        }
    }

    private fun removeCustomResourceOperator(resource: CustomResourceDefinition) {
        val kind = ResourceKind.create(resource.spec) ?: return
        watch.stopWatch(kind)
        val operators = when (toResourcesIn(resource.spec)) {
            CURRENT_NAMESPACE -> namespacedOperators
            ANY_NAMESPACE,
            NO_NAMESPACE -> nonNamespacedOperators
        }
        operators.remove(kind)
    }

    private fun toResourcesIn(spec: CustomResourceDefinitionSpec): ResourcesIn {
        return when (spec.scope) {
            Scope.CLUSTER.value() -> NO_NAMESPACE
            Scope.NAMESPACED.value() -> CURRENT_NAMESPACE
            else -> throw IllegalArgumentException(
                    "Could not determine scope in spec for custom resource definition ${spec.names.kind}")
        }
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        return singleResourceOperator.get(resource)
    }

    override fun create(resource: HasMetadata): HasMetadata? {
        return singleResourceOperator.create(resource)
    }

    override fun replace(resource: HasMetadata): HasMetadata? {
        return singleResourceOperator.replace(resource)
    }

    override fun watch(kind: ResourceKind<out HasMetadata>) {
        logger<ActiveContext<*, *>>().debug("Watching $kind resources.")
        watch(namespacedOperators[kind])
        watch(nonNamespacedOperators[kind])
    }

    override fun watchAll(kinds: Collection<ResourceKind<out HasMetadata>>) {
        kinds.forEach { kind -> watch(kind) }
    }

    override fun watch(definition: CustomResourceDefinition) {
        watch(getOperator(definition))
    }

    override fun getWatched(): Collection<ResourceKind<out HasMetadata>> {
        return watch.getWatched()
    }

    private fun watch(operator: IResourceOperator<*>?) {
        if (operator == null) {
            return
        }

        watch.watch(operator.kind, operator::watchAll, watchListener)
    }

    override fun watch(resource: HasMetadata, watcher: Watcher<HasMetadata>): Watch? {
        return singleResourceOperator.watch(resource, watcher)
    }

    override fun stopWatch(kind: ResourceKind<out HasMetadata>) {
        logger<ActiveContext<*, *>>().debug("Stop watching $kind resources.")
        watch.stopWatch(kind)
        // don't notify invalidation change because this would cause UI to reload
        // and therefore to repopulate the cache immediately.
        // Any resource operation that eventually happens while the watch is not active would cause the cache
        // to become out-of-sync and it would therefore return invalid resources when asked to do so
        invalidateOperators(kind)
    }

    override fun stopWatch(definition: CustomResourceDefinition) {
        val kind = ResourceKind.create(definition.spec) ?: return
        stopWatch(kind)
    }

    override fun added(resource: HasMetadata): Boolean {
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
        // we need to add resource to both operators (ex. all pods & only namespaced pods)
        val kind = ResourceKind.create(resource)
        val addedToNonNamespaced = addResource(resource, nonNamespacedOperators[kind])
        val addedToNamespaced = getCurrentNamespace() == resource.metadata.namespace
                && addResource(resource, namespacedOperators[kind])
        return addedToNonNamespaced ||
                addedToNamespaced
    }

    private fun addResource(resource: CustomResourceDefinition): Boolean {
        val added = addResource(resource as HasMetadata)
        if (added) {
            val kind = ResourceKind.create(resource.spec)
            if (kind != null) {
                createCustomResourcesOperator(resource, kind)
            }
        }
        return added
    }

    private fun addResource(resource: HasMetadata, operator: IResourceOperator<out HasMetadata>?): Boolean {
        if (operator == null) {
            return false
        }
        return operator.added(resource)
    }

    override fun removed(resource: HasMetadata): Boolean {
        logger<ActiveContext<*, *>>().debug("Resource ${resource.metadata.name} was removed.")
        val removed = if (resource is CustomResourceDefinition) {
            // implicit cast to CustomResourceDefinition
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
        // we need to remove resource from both operators
        val removedNonNamespaced = removeResource(resource, nonNamespacedOperators[kind])
        val removedNamespaced = (getCurrentNamespace() == resource.metadata.namespace) &&
                removeResource(resource, namespacedOperators[kind])
        return removedNonNamespaced
                || removedNamespaced
    }

    private fun removeResource(definition: CustomResourceDefinition): Boolean {
        val removed = removeResource(definition as HasMetadata)
        if (removed) {
            removeCustomResourceOperator(definition)
        }
        return removed
    }

    private fun removeResource(resource: HasMetadata, operator: IResourceOperator<out HasMetadata>?): Boolean {
        if (operator == null) {
            return false
        }
        return operator.removed(resource)
    }

    override fun invalidate() {
        logger<ActiveContext<*, *>>().debug("Invalidating all cached resources.")
        namespacedOperators.values.forEach { it.invalidate() }
        nonNamespacedOperators.values.forEach { it.invalidate() }
        modelChange.fireModified(this)
    }

    override fun replaced(resource: HasMetadata): Boolean {
        logger<ActiveContext<*, *>>().debug("Resource ${resource.metadata.name} was replaced.")
        val replaced = when(resource) {
            is CustomResourceDefinition ->
                replaced(ResourceKind.create(resource.spec), resource)
            else ->
                replaced(ResourceKind.create(resource), resource)
        }
        if (replaced) {
            modelChange.fireModified(resource)
        }
        return replaced
    }

    private fun replaced(kind: ResourceKind<out HasMetadata>?, resource: HasMetadata): Boolean {
        if (kind == null) {
            return false
        }
        val replaceNamespaced = namespacedOperators[kind]?.replaced(resource) ?: false
        val replaceNonNamespaced = nonNamespacedOperators[kind]?.replaced(resource) ?: false
        return replaceNamespaced
                || replaceNonNamespaced
    }

    override fun invalidate(kind: ResourceKind<*>) {
        logger<ActiveContext<*, *>>().debug("Invalidating all $kind resources.")
        invalidateOperators(kind)
    }

    private fun invalidateOperators(kind: ResourceKind<*>) {
        namespacedOperators[kind]?.invalidate()
        nonNamespacedOperators[kind]?.invalidate()
    }

    protected open fun namespacedOperators(namespace: String?): List<INamespacedResourceOperator<out HasMetadata, C>> {
        return namespacedOperators.values
                .filter { it.namespace == namespace }
    }

    override fun delete(resources: List<HasMetadata>, force: Boolean) {
        logger<ActiveContext<*, *>>().debug("Deleting ${toMessage(resources)}.")
        val exceptions = resources
            .distinct()
            .groupBy { Pair(ResourceKind.create(it), ResourcesIn.valueOf(it, getCurrentNamespace())) }
            .mapNotNull {
                try {
                    delete(it.key.first, it.key.second, it.value, force)
                    modelChange.fireModified(it.value)
                    null
                } catch (e: KubernetesClientException) {
                    ResourceException("Could not delete ${it.key.first} resource(s) ${toMessage(resources, -1)}", e, it.value)
                }
            }
        if (exceptions.isNotEmpty()) {
            val failedDelete = exceptions.flatMap { it.resources }
            throw MultiResourceException("Could not delete resource(s) ${toMessage(failedDelete, -1)}", exceptions)
        }
    }

    private fun delete(kind: ResourceKind<out HasMetadata>, scope: ResourcesIn, resources: List<HasMetadata>, force: Boolean): Collection<HasMetadata> {
        val operator = getOperator(kind, scope)
        if (operator == null) {
            logger<ActiveContext<*,*>>().warn(
                "Could not delete $kind resources: ${toMessage(resources, -1)}."
                + "No operator found for $kind in scope $scope.)")
            return emptyList()
        }
        try {
            val deleted = operator.delete(resources, force)
            return if (deleted) {
                resources.forEach { setWillBeDeleted(it) }
                resources
            } else {
                throw KubernetesClientException("Could not delete $kind resources: ${toMessage(resources, -1)} ")
            }
        } catch (e: KubernetesClientException) {
            throw KubernetesClientException("Could not delete $kind resources: ${toMessage(resources, -1)} ", e)
        }
    }

    override fun close() {
        logger<ActiveContext<*, *>>().debug("Closing context ${context.name}.")
        watch.close()
        dashboard.close()
    }

    private fun <P: IResourceOperator<out HasMetadata>> getAllResourceOperators(type: Class<P>)
            : MutableMap<ResourceKind<out HasMetadata>, P> {
        val operators = mutableMapOf<ResourceKind<out HasMetadata>, P>()
        operators.putAll(
                getInternalResourceOperators(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        operators.putAll(
                getExtensionResourceOperators(client)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        return operators
    }

    protected abstract fun getInternalResourceOperators(client: ClientAdapter<out C>): List<IResourceOperator<out HasMetadata>>

    protected open fun getExtensionResourceOperators(client: ClientAdapter<out C>): List<IResourceOperator<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(client.get()) }
    }
}
