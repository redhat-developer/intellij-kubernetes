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

interface IModelChangeObservable {
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
    fun fireCurrentNamespace(namespace: String?)
    fun fireRemoved(removed: Any)
    fun fireAdded(added: Any)
    fun fireModified(modified: Any)
}

open class ModelChangeObservable: IModelChangeObservable {

    interface IResourceChangeListener {
        fun currentNamespace(namespace: String?) = Unit
        fun removed(removed: Any) = Unit
        fun added(added: Any) = Unit
        fun modified(modified: Any) = Unit
    }

    protected open val listeners = mutableListOf<IResourceChangeListener>()

    override fun addListener(listener: IResourceChangeListener) {
        if (listeners.contains(listener)) {
            return
        }
        listeners.add(listener)
    }

    override fun fireRemoved(removed: Any) {
        listeners.forEach { it.removed(removed) }
    }

    override fun fireAdded(added: Any) {
        listeners.forEach { it.added(added) }
    }

    override fun fireModified(modified: Any) {
        listeners.forEach { it.modified(modified) }
    }

    override fun fireCurrentNamespace(namespace: String?) {
        listeners.forEach { it.currentNamespace(namespace) }
    }
}