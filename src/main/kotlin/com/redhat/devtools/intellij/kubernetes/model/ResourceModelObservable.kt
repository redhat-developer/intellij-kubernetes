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

import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext

interface IResourceModelListener {
    fun currentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*,*>?) = Unit
    fun removed(removed: Any) = Unit
    fun added(added: Any) = Unit
    fun modified(modified: Any) = Unit
}

interface IResourceModelObservable {
    fun addListener(listener: IResourceModelListener)
    fun removeListener(listener: IResourceModelListener)
    fun fireAllContextsChanged()
    fun fireCurrentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*,*>?)
    fun fireModified(modified: Any)
    fun fireRemoved(removed: Any)
    fun fireAdded(added: Any)
}

open class ResourceModelObservable: IResourceModelObservable {

    protected open val listeners = mutableListOf<IResourceModelListener>()

    override fun addListener(listener: IResourceModelListener) {
        if (listeners.contains(listener)) {
            return
        }
        listeners.add(listener)
    }

    override fun removeListener(listener: IResourceModelListener) {
        if (!listeners.contains(listener)) {
            return
        }
        listeners.remove(listener)
    }

    override fun fireAllContextsChanged() {
        fireModified(IResourceModel.getInstance())
    }

    override fun fireCurrentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*, *>?) {
        listeners.forEach { it.currentNamespaceChanged(new, old) }
    }

    override fun fireModified(modified: Any) {
        listeners.forEach { it.modified(modified) }
    }

    override fun fireRemoved(removed: Any) {
        listeners.forEach { it.removed(removed) }
    }

    override fun fireAdded(added: Any) {
        listeners.forEach { it.added(added) }
    }
}