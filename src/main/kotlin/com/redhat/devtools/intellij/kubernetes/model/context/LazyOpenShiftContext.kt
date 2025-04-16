/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.context

import com.redhat.devtools.intellij.kubernetes.model.IResourceModelObservable
import com.redhat.devtools.intellij.kubernetes.model.client.KubeClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.client.OSClientAdapter
import com.redhat.devtools.intellij.kubernetes.model.resource.IResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.KubernetesReplicas.Replicator
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import org.jetbrains.concurrency.runAsync
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A delegating context that starts with a Kubernetes delegate.
 * It then (async) tries to create and use an OpenShift context, notifying the change if it successfully could.
 * It sticks to the Kubernetes if it can't.
 *
 * @see createOpenShiftDelegate
 */
class LazyOpenShiftContext(
    context: NamedContext,
    modelChange: IResourceModelObservable,
    client: KubeClientAdapter,
    /* for testing purposes */
    kubernetesContextFactory: (
        context: NamedContext,
        modelChange: IResourceModelObservable,
        client: KubeClientAdapter,
    ) -> IActiveContext<out HasMetadata, out KubernetesClient>
    = ::KubernetesContext,
    /* for testing purposes */
    private val openshiftContextFactory: (
        client: OSClientAdapter,
        modelChange: IResourceModelObservable
    ) -> IActiveContext<out HasMetadata, out KubernetesClient>?
    = IActiveContext.Factory::createOpenShift,
    /* for testing purposes */
    runAsync: (runnable: () -> Unit) -> Unit
    = ::runAsync
) : KubernetesContext(context, modelChange, client) {

    private val lock = ReentrantReadWriteLock()
    private var delegate: IActiveContext<out HasMetadata, out KubernetesClient> =
        kubernetesContextFactory.invoke(context, modelChange, client)

    init {
        runAsync.invoke {
            createOpenShiftDelegate()
        }
    }

    override val namespaceKind: ResourceKind<out HasMetadata>
        get() = delegate.namespaceKind

    override fun getInternalResourceOperators(): List<IResourceOperator<out HasMetadata>> {
        lock.read {
            return delegate.getInternalResourceOperators()
        }
    }

    override fun isOpenShift(): Boolean {
        lock.read {
            return delegate.isOpenShift()
        }
    }

    override fun setReplicas(replicas: Int, replicator: Replicator) {
        lock.read {
            delegate.setReplicas(replicas, replicator)
        }
    }

    override fun getReplicas(resource: HasMetadata): Replicator? {
        lock.read {
            return delegate.getReplicas(resource)
        }
    }

    override fun getDashboardUrl(): String? {
        lock.read {
            return delegate.getDashboardUrl()
        }
    }

    private fun createOpenShiftDelegate() {
        if (client.canAdaptToOpenShift()) {
            val delegate = openshiftContextFactory.invoke(
                client.toOpenShift(),
                modelChange
            ) ?: return
            lock.write {
                this.delegate = delegate
            }
            modelChange.fireModified(this)
        }
    }
}

