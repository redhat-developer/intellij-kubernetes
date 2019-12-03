package org.jboss.tools.intellij.kubernetes.model

interface ResourceChangeObservable {
    fun addListener(listener: ResourceChangedObservableImpl.ResourcesChangedListener)
    fun fireRemoved(removed: List<Any>)
    fun fireAdded(added: List<Any>)
    fun fireModified(removed: List<Any>)
}

open class ResourceChangedObservableImpl: ResourceChangeObservable {

    interface ResourcesChangedListener {
        fun removed(removed: List<Any>)
        fun added(removed: List<Any>)
        fun modified(removed: List<Any>)
    }

    private var listeners = mutableListOf<ResourcesChangedListener>()

    override fun addListener(listener: ResourcesChangedListener) {
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