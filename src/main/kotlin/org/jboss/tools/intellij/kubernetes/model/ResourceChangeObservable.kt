/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.model

interface ResourceChangeObservable {
    fun addListener(listener: ResourceChangedObservableImpl.ResourceChangeListener)
    fun fireRemoved(removed: List<Any>)
    fun fireAdded(added: List<Any>)
    fun fireModified(removed: List<Any>)
}

open class ResourceChangedObservableImpl: ResourceChangeObservable {

    interface ResourceChangeListener {
        fun removed(removed: List<Any>)
        fun added(removed: List<Any>)
        fun modified(modified: List<Any>)
    }

    open class ResourceChangeAdapter: ResourceChangeListener {
        override fun removed(removed: List<Any>) = Unit
        override fun added(removed: List<Any>) = Unit
        override fun modified(modified: List<Any>) = Unit
    }

    private var listeners = mutableListOf<ResourceChangeListener>()

    override fun addListener(listener: ResourceChangeListener) {
        listeners.add(listener)
    }

    override fun fireRemoved(removed: List<Any>) {
        listeners.forEach{ listener -> listener.removed(removed)}
    }

    override fun fireAdded(added: List<Any>) {
        listeners.forEach{ listener -> listener.added(added)}
    }

    override fun fireModified(removed: List<Any>) {
        listeners.forEach { listener -> listener.modified(removed) }
    }
}