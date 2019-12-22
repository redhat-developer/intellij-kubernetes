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

interface IResourceChangeObservable {
    fun addListener(listener: ResourceChangeObservable.ResourceChangeListener)
    fun fireRemoved(removed: Any)
    fun fireAdded(added: Any)
    fun fireModified(removed: Any)
}

open class ResourceChangeObservable: IResourceChangeObservable {

    interface ResourceChangeListener {
        fun removed(removed: Any) = Unit
        fun added(added: Any) = Unit
        fun modified(modified: Any) = Unit
    }

    private var listeners = mutableListOf<ResourceChangeListener>()

    override fun addListener(listener: ResourceChangeListener) {
        listeners.add(listener)
    }

    override fun fireRemoved(removed: Any) {
        listeners.forEach { it.removed(removed) }
    }

    override fun fireAdded(added: Any) {
        listeners.forEach { it.added(added) }
    }

    override fun fireModified(removed: Any) {
        listeners.forEach { it.modified(removed) }
    }
}