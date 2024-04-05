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
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.redhat.devtools.intellij.common.utils.ConfigHelper
import com.redhat.devtools.intellij.common.utils.ConfigWatcher
import com.redhat.devtools.intellij.common.utils.ExecHelper
import com.redhat.devtools.intellij.kubernetes.model.client.ClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig
import com.redhat.devtools.intellij.kubernetes.model.context.Context
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.context.IContext
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.util.ResettableLazyProperty
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.NAME_PREFIX_CONTEXT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_IS_OPENSHIFT
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_KUBERNETES_VERSION
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService.PROP_OPENSHIFT_VERSION
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import java.nio.file.Paths
import java.util.concurrent.CompletionException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface IAllContexts {
	/**
	 * The current context. Is also contained in [all].
	 */
	val current: IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * All contexts that are available. These are loaded from the kube-config file.
	 */
	val all: List<IContext>

	/**
	 * Sets the given context as current context. The old context is closed.
	 *
	 * @param context the context to set as current context
	 * @return new active context
	 *
	 * @see current
	 */
	fun setCurrentContext(context: IContext): IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * Sets the given namespace as current namespace.
	 * A new context is created and set. This is done because the fabric8 client is not able to switch namespace.
	 *
	 * @param namespace the namespace to set as current namespace
	 * @return new active context with the given namespace as current namespace
	 *
	 * @see current
	 */
	fun setCurrentNamespace(namespace: String): IActiveContext<out HasMetadata, out KubernetesClient>?

	/**
	 * Invalidates all contexts and the current context.
	 * Causes them to be reloaded from config when accessed.
	 */
	fun refresh()
}

open class AllContexts(
	private val contextFactory: (ClientAdapter<out KubernetesClient>, IResourceModelObservable) -> IActiveContext<out HasMetadata, out KubernetesClient>? =
		IActiveContext.Factory::create,
	private val modelChange: IResourceModelObservable,
	private val clientFactory: (
		namespace: String?,
		context: String?
	) -> ClientAdapter<out KubernetesClient>
	= { namespace, context -> ClientAdapter.Factory.create(namespace, context) }
) : IAllContexts {

	init {
		watchKubeConfig()
	}

	private val lock = ReentrantReadWriteLock()

	private val client = ResettableLazyProperty {
		lock.write {
			clientFactory.invoke(null, null)
		}
	}

	override val current: IActiveContext<out HasMetadata, out KubernetesClient>?
		get() {
			return findActive(all)
		}

	private val _all: MutableList<IContext> = mutableListOf()

	override val all: List<IContext>
		get() {
			lock.write {
				if (_all.isEmpty()) {
					val all = createContexts(client.get(), client.get()?.config)
						_all.addAll(all)
				}
				return _all
			}
		}

	override fun setCurrentContext(context: IContext): IActiveContext<out HasMetadata, out KubernetesClient>? {
		if (current == context) {
			return current
		}
		val newClient = clientFactory.invoke(context.context.context.namespace, context.context.name)
		val new = setCurrentContext(newClient, emptyList())
		if (new != null) {
			modelChange.fireAllContextsChanged()
		}
		return new
	}

	override fun setCurrentNamespace(namespace: String): IActiveContext<out HasMetadata, out KubernetesClient>? {
		val old = this.current ?: return null
		val newClient = clientFactory.invoke(namespace, old.context.name)
		val new = setCurrentContext(newClient, old.getWatched())
		if (new != null) {
			modelChange.fireCurrentNamespaceChanged(new, old)
		}
		return new
	}

	private fun setCurrentContext(
		newClient: ClientAdapter<out KubernetesClient>,
		toWatch: Collection<ResourceKind<out HasMetadata>>?,
	) : IActiveContext<out HasMetadata, out KubernetesClient>? {
		lock.write {
			try {
				replaceClient(newClient, this.client.get())
				newClient.config.save().join()
				current?.close()
				clearAllContexts() // causes reload of all contexts when accessed afterwards
				val newCurrent = current // gets new current from all
				if (toWatch != null) {
					newCurrent?.watchAll(toWatch)
				}
				return newCurrent
			} catch (e: CompletionException) {
				val cause = e.cause ?: throw e
				throw cause
			}
		}
	}

	private fun clearAllContexts() {
		_all.clear()
	}

	override fun refresh() {
		lock.write {
			this.current?.close()
			clearAllContexts() // latter access will cause reload
		}
		modelChange.fireAllContextsChanged()
	}

	private fun findActive(all: List<IContext>): IActiveContext<out HasMetadata, out KubernetesClient>? {
		return if (all.isNotEmpty()) {
			val activeContext = all.firstOrNull { it.active }
			activeContext as? IActiveContext<out HasMetadata, out KubernetesClient>
		} else {
			null
		}
	}

	private fun createContexts(client: ClientAdapter<out KubernetesClient>?, config: ClientConfig?)
			: List<IContext> {
		if (client == null
			|| config == null
		) {
			return emptyList()
		}
		lock.read {
			return config.allContexts
				.map {
					if (config.isCurrent(it)) {
						createActiveContext(client) ?: Context(it)
					} else {
						Context(it)
					}
				}
		}
	}

	private fun replaceClient(new: ClientAdapter<out KubernetesClient>, old: ClientAdapter<out KubernetesClient>?)
			: ClientAdapter<out KubernetesClient> {
		old?.close()
		this.client.set(new)
		return new
	}

	private fun createActiveContext(client: ClientAdapter<out KubernetesClient>?)
			: IActiveContext<out HasMetadata, out KubernetesClient>? {
		if (client == null) {
			return null
		}

		val context = contextFactory.invoke(client, modelChange) ?: return null
		reportTelemetry(context)
		return context
	}

	protected open fun reportTelemetry(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
		ExecHelper.submit {
			val telemetry = TelemetryService.instance.action(NAME_PREFIX_CONTEXT + "use")
				.property(PROP_IS_OPENSHIFT, context.isOpenShift().toString())
			try {
				telemetry
					.property(PROP_KUBERNETES_VERSION, context.version.kubernetesVersion)
					.property(PROP_OPENSHIFT_VERSION, context.version.openshiftVersion)
					.send()
			} catch (e: RuntimeException) {
				telemetry
					.property(PROP_KUBERNETES_VERSION, "error retrieving")
					.property(PROP_OPENSHIFT_VERSION, "error retrieving")
					.send()
				logger<AllContexts>().warn("Could not report context/cluster versions", e)
			}
		}
	}

	protected open fun watchKubeConfig() {
		val path = Paths.get(Config.getKubeconfigFilename())
		/**
		 * [ConfigWatcher] cannot add/remove listeners nor can it get closed (and stop the [java.nio.file.WatchService]).
		 * We therefore have to create a single instance in here rather than using it in a shielded/private way within
		 * [com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig].
		 * Closing/Recreating [ConfigWatcher] is needed when used within [com.redhat.devtools.intellij.kubernetes.model.client.ClientConfig].
		 * The latter gets closed/recreated whenever the context changes in
		 * [com.redhat.devtools.intellij.kubernetes.model.client.KubeConfigAdapter].
		 */
		val watcher = ConfigWatcher(path) { _, config: io.fabric8.kubernetes.api.model.Config? -> onKubeConfigChanged(config) }
		runAsync(watcher::run)
	}

	protected open fun onKubeConfigChanged(fileConfig: io.fabric8.kubernetes.api.model.Config?) {
		lock.read {
			fileConfig ?: return
			val client = client.get() ?: return
			val clientConfig = client.config.configuration
			if (ConfigHelper.areEqual(fileConfig, clientConfig)) {
				return
			}
			this.client.reset() // create new client when accessed
			client.close()
		}
		refresh()
	}

	/** for testing purposes */
	protected open fun runAsync(runnable: () -> Unit) {
		ApplicationManager.getApplication().executeOnPooledThread(runnable)
	}
}