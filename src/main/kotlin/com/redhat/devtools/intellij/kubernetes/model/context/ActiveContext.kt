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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import com.redhat.devtools.intellij.kubernetes.model.IModelChangeObservable
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch
import com.redhat.devtools.intellij.kubernetes.model.ResourceWatch.WatchListeners
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.ANY_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.CURRENT_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn.NO_NAMESPACE
import com.redhat.devtools.intellij.kubernetes.model.resource.INamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.INonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperatorFactory
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceScope
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.GenericCustomResource
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.NamespacedCustomResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.NonNamespacedCustomResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceDefinitionContextFactory
import com.redhat.devtools.intellij.kubernetes.model.util.MultiResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.isSameResource
import com.redhat.devtools.intellij.kubernetes.model.util.setWillBeDeleted
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.client.Config
import java.net.URL

abstract class ActiveContext<N : HasMetadata, C : KubernetesClient>(
    private val modelChange: IModelChangeObservable,
    private val clients: Clients<C>,
    context: NamedContext
) : Context(context), IActiveContext<N, C> {

    override val active: Boolean = true
    override val masterUrl: URL
        get() {
            return clients.get().masterUrl
        }
    override val version: ClusterInfo by lazy {
        ClusterHelper.getClusterInfo(clients.get())
    }

    private val extensionName: ExtensionPointName<IResourceOperatorFactory<HasMetadata, C, IResourceOperator<HasMetadata>>> =
            ExtensionPointName.create("com.redhat.devtools.intellij.kubernetes.resourceOperators")

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

    override fun setCurrentNamespace(namespace: String): Boolean {
        val currentNamespace = getCurrentNamespace()
        if (namespace == currentNamespace) {
            return false
        }
        logger<ActiveContext<*, *>>().debug("Setting current namespace to $namespace.")

        val stopped = stopWatch(currentNamespace)
        val configuration = clients.get().configuration
        setCurrentNamespace(namespace, configuration)
        setCurrentNamespace(namespace, namespacedOperators.values)
        watchAll(stopped)
        modelChange.fireCurrentNamespace(namespace)
        return true
    }

    private fun setCurrentNamespace(namespace: String, configuration: Config) {
        configuration.namespace = namespace
    }

    private fun setCurrentNamespace(operators: Collection<INamespacedResourceOperator<*, *>>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val namespacesOperator: INonNamespacedResourceOperator<N, C> = nonNamespacedOperators[getNamespacesKind()]
                    as INonNamespacedResourceOperator<N, C>
            val namespace = getCurrentNamespace(namespacesOperator.allResources)
            setCurrentNamespace(namespace?.metadata?.name, operators)
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
        val namespaceKind = getNamespacesKind()
        val current = getCurrentNamespace(getAllResources(namespaceKind, NO_NAMESPACE))
        return current?.metadata?.name
    }

    private fun getCurrentNamespace(namespaces: Collection<N>): N? {
        val name: String? = clients.get().configuration.namespace
        return find(name, namespaces)
    }

    private fun find(namespace: String?, namespaces: Collection<N>): N? {
        if (namespace == null) {
            return null
        }
        return namespaces.find { namespace == it.metadata?.name }
    }

    protected abstract fun getNamespacesKind(): ResourceKind<N>

    override fun isCurrentNamespace(resource: HasMetadata): Boolean {
        val current = getCurrentNamespace(getAllResources(getNamespacesKind(), NO_NAMESPACE)) ?: return false
        return resource.isSameResource(current as HasMetadata)
    }

    override fun <R: HasMetadata> getAllResources(kind: ResourceKind<R>, resourcesIn: ResourcesIn): Collection<R> {
        logger<ActiveContext<*,*>>().debug("Resources $kind requested.")
        synchronized(this) {
            val operator = getOperator(kind, resourcesIn)
            return operator?.allResources
                ?: emptyList()
        }
    }

    private fun setOperator(
        operator: IResourceOperator<out HasMetadata>,
        kind: ResourceKind<GenericCustomResource>,
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

    private fun getOperator(definition: CustomResourceDefinition): IResourceOperator<GenericCustomResource>? {
        val kind = ResourceKind.create(definition.spec) ?: return null
        val resourcesIn = toResourcesIn(definition.spec)
        synchronized(this) {
            var operator: IResourceOperator<GenericCustomResource>? = getOperator(kind, resourcesIn)
            if (operator == null) {
                operator = createCustomResourcesOperator(definition, kind)
            }
            return operator
        }
    }

    override fun getAllResources(definition: CustomResourceDefinition): Collection<GenericCustomResource> {
        return getOperator(definition)?.allResources ?: return emptyList()
    }

    private fun createCustomResourcesOperator(
            definition: CustomResourceDefinition,
            kind: ResourceKind<GenericCustomResource>)
            : IResourceOperator<GenericCustomResource>? {
        synchronized(this) {
            val resourceIn = toResourcesIn(definition.spec)
            val operator = createCustomResourcesOperator(definition, resourceIn) ?: return null
            setOperator(operator, kind, resourceIn)
            return operator
        }
    }

    protected open fun createCustomResourcesOperator(
            definition: CustomResourceDefinition,
            resourceIn: ResourcesIn)
            : IResourceOperator<GenericCustomResource>? {
        val kind = ResourceKind.create(definition.spec) ?: return null
        val context = CustomResourceDefinitionContextFactory.create(definition)
        return when(resourceIn) {
            CURRENT_NAMESPACE ->
                NamespacedCustomResourceOperator(
                    kind,
                    context,
                    getCurrentNamespace(),
                    clients.get()
                )
            ANY_NAMESPACE,
            NO_NAMESPACE ->
                NonNamespacedCustomResourceOperator(
                    kind,
                    context,
                    clients.get())
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
            CustomResourceScope.CLUSTER -> NO_NAMESPACE
            CustomResourceScope.NAMESPACED -> CURRENT_NAMESPACE
            else -> throw IllegalArgumentException(
                    "Could not determine scope in spec for custom resource definition ${spec.names.kind}")
        }
    }

    override fun watch(kind: ResourceKind<out HasMetadata>) {
        logger<ActiveContext<*, *>>().debug("Watching $kind resources.")
        watch(namespacedOperators[kind])
        watch(nonNamespacedOperators[kind])
    }

    override fun watch(definition: CustomResourceDefinition) {
        watch(getOperator(definition))
    }

    private fun watchAll(kinds: Collection<Any>) {
        val watchOperations = namespacedOperators.entries.toList()
            .filter { kinds.contains(it.key) }
            .map { Pair(it.value.kind, it.value::watchAll) }

        watch.watchAll(watchOperations, watchListener)
    }

    private fun watch(operator: IResourceOperator<*>?) {
        if (operator == null) {
            return
        }

        watch.watch(operator.kind, operator::watchAll, watchListener)
    }

    override fun stopWatch(kind: ResourceKind<out HasMetadata>) {
        logger<ActiveContext<*, *>>().debug("Stop watching $kind resources.")
        watch.stopWatch(kind)
        // dont notify invalidation change because this would cause UI to reload
        // and therefore to repopulate the cache immediately.
        // Any resource operation that eventually happens while the watch is not active would cause the cache
        // to become out-of-sync and it would therefore return invalid resources when asked to do so
        invalidateOperators(kind)
    }

    override fun stopWatch(definition: CustomResourceDefinition) {
        val kind = ResourceKind.create(definition.spec) ?: return
        stopWatch(kind)
    }

    /**
     * Stops watching all watchables for the given namespace. Doesn't stop the cluster wide watchables nor
     * the ones for a different namespace.
     *
     * @param namespace the namespace that the watchables should be stopped for
     */
    private fun stopWatch(namespace: String?): Collection<Any> {
        logger<ActiveContext<*, *>>().debug("Stopping all watches for namespace $namespace.")
        return watch.stopWatchAll(namespacedOperators(namespace).map { it.kind })
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
        logger<ActiveContext<*, *>>().debug("Invalidating all operators.")
        namespacedOperators.values.forEach { it.invalidate() }
        nonNamespacedOperators.values.forEach { it.invalidate() }
        modelChange.fireModified(this)
    }

    override fun replaced(resource: HasMetadata): Boolean {
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
        logger<ActiveContext<*, *>>().debug("Invalidating resource operator for $kind resources.")
        invalidateOperators(kind)
        modelChange.fireModified(kind)
    }

    private fun invalidateOperators(kind: ResourceKind<*>) {
        namespacedOperators[kind]?.invalidate()
        nonNamespacedOperators[kind]?.invalidate()
    }

    protected open fun namespacedOperators(namespace: String?): List<INamespacedResourceOperator<out HasMetadata, C>> {
        return namespacedOperators.values
                .filter { it.namespace == namespace }
    }

    override fun delete(resources: List<HasMetadata>) {
        val exceptions = resources
            .distinct()
            .groupBy { Pair(ResourceKind.create(it), ResourcesIn.valueOf(it, getCurrentNamespace())) }
            .mapNotNull {
                try {
                    delete(it.key.first, it.key.second, it.value)
                    null
                } catch (e: KubernetesClientException) {
                    ResourceException(it.value, "Could not delete ${it.key.first} resource(s) ${toMessage(resources, -1)}", e)
                }
            }
        if (exceptions.isNotEmpty()) {
            val failedDelete = exceptions.flatMap { it.resources }
            throw MultiResourceException("Could not delete resource(s) ${toMessage(failedDelete, -1)}", exceptions)
        }
    }

    private fun delete(kind: ResourceKind<out HasMetadata>, scope: ResourcesIn, resources: List<HasMetadata>) {
        val operator = getOperator(kind, scope)
        if (operator == null) {
            logger<ActiveContext<*,*>>().warn("""Could not delete $kind resources: ${toMessage(resources, -1)}.
                |No operator found for in scope $scope.""".trimMargin())
            return
        }
        try {
            val deleted = operator.delete(resources)
            if (deleted) {
                resources.forEach { setWillBeDeleted(it) }
                modelChange.fireModified(resources)
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
        clients.close()
    }

    private fun <P: IResourceOperator<out HasMetadata>> getAllResourceOperators(type: Class<P>)
            : MutableMap<ResourceKind<out HasMetadata>, P> {
        val operators = mutableMapOf<ResourceKind<out HasMetadata>, P>()
        operators.putAll(
                getInternalResourceOperators(clients)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        operators.putAll(
                getExtensionResourceOperators(clients)
                        .filterIsInstance(type)
                        .associateBy { it.kind })
        return operators
    }

    protected abstract fun getInternalResourceOperators(clients: Clients<C>): List<IResourceOperator<out HasMetadata>>

    protected open fun getExtensionResourceOperators(clients: Clients<C>): List<IResourceOperator<out HasMetadata>> {
        return extensionName.extensionList
                .map { it.create(clients) }
    }
}
