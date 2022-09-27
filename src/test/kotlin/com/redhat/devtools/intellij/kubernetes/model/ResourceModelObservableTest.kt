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

import com.nhaarman.mockitokotlin2.mock
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import io.fabric8.kubernetes.api.model.Namespace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class ResourceModelObservableTest {

    private val observable = TestableResourceModelObservable()
    private val resource = resource<Namespace>("smurfette namespace", null, "smurfetteUid", "v1")
    private val listener = object: IResourceModelListener {

        var newContext: IActiveContext<*,*>? = null
        var oldContext: IActiveContext<*,*>? = null
        val removedResources = mutableListOf<Any>()
        val addedResources = mutableListOf<Any>()
        val modifiedResources = mutableListOf<Any>()

        override fun currentNamespaceChanged(new: IActiveContext<*,*>?, old: IActiveContext<*,*>?) {
            this.newContext = new
            this.oldContext = old
        }

        override fun removed(removed: Any) {
            removedResources.add(removed)
        }

        override fun added(added: Any) {
            addedResources.add(added)
        }

        override fun modified(modified: Any) {
            modifiedResources.add(modified)
        }
    }

    @Before
    fun before() {
        observable.addListener(listener)
    }

    @Test
    fun `#addListener should not add the same listener twice`() {
        // given
        assertThat(observable.listeners.contains(listener)).isTrue()
        val sizeBeforeAdd = observable.listeners.size
        // when
        observable.addListener(listener)
        // then
        assertThat(observable.listeners.size).isEqualTo(sizeBeforeAdd)
        assertThat(observable.listeners.contains(listener)).isTrue()
    }

    @Test
    fun `#addListener should add listener that is not contained yet`() {
        // given
        val sizeBeforeAdd = observable.listeners.size
        val newListener = object: IResourceModelListener {}
        assertThat(observable.listeners.contains(newListener)).isFalse()
        // when
        observable.addListener(newListener)
        // then
        assertThat(observable.listeners.size).isEqualTo(sizeBeforeAdd + 1)
        assertThat(observable.listeners.contains(newListener)).isTrue()
    }

    @Test
    fun `#removeListener should remove listener that is contained`() {
        // given
        val newListener = object: IResourceModelListener {}
        observable.addListener(newListener)
        assertThat(observable.listeners.contains(newListener)).isTrue()
        val sizeBeforeRemove = observable.listeners.size
        // when
        observable.removeListener(newListener)
        // then
        assertThat(observable.listeners.size).isEqualTo(sizeBeforeRemove - 1)
        assertThat(observable.listeners.contains(newListener)).isFalse()
    }

    @Test
    fun `#removeListener should NOT remove listener that is NOT contained`() {
        // given
        val newListener = object: IResourceModelListener {}
        assertThat(observable.listeners.contains(newListener)).isFalse()
        val sizeBeforeRemove = observable.listeners.size
        // when
        observable.removeListener(newListener)
        // then
        assertThat(observable.listeners.size).isEqualTo(sizeBeforeRemove)
        assertThat(observable.listeners.contains(newListener)).isFalse()
    }

    @Test
    fun `#fireRemoved should notify removed resource`() {
        // given
        // when
        observable.fireRemoved(resource)
        // then
        assertThat(listener.removedResources.take(1)).containsExactly(resource)
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources).isEmpty()
        assertThat(listener.newContext).isNull()
        assertThat(listener.oldContext).isNull()
    }

    @Test
    fun `#fireAdded should notify added resource`() {
        // given
        // when
        observable.fireAdded(resource)
        // then
        assertThat(listener.removedResources).isEmpty()
        assertThat(listener.addedResources.take(1)).containsExactly(resource)
        assertThat(listener.modifiedResources).isEmpty()
        assertThat(listener.newContext).isNull()
        assertThat(listener.oldContext).isNull()
    }

    @Test
    fun `#fireModified should notify modified resource`() {
        // given
        // when
        observable.fireModified(resource)
        // then
        assertThat(listener.removedResources).isEmpty()
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources.take(1)).containsExactly(resource)
        assertThat(listener.newContext).isNull()
        assertThat(listener.oldContext).isNull()
    }

    @Test
    fun `#fireCurrentNamespace should notify current namespace`() {
        // given
        val newContext: IActiveContext<*,*> = mock()
        val oldContext: IActiveContext<*,*> = mock()
        // when
        observable.fireCurrentNamespaceChanged(newContext, oldContext)
        // then
        assertThat(listener.removedResources).isEmpty()
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources).isEmpty()
        assertThat(listener.newContext).isEqualTo(newContext)
        assertThat(listener.oldContext).isEqualTo(oldContext)
    }

    class TestableResourceModelObservable: ResourceModelObservable() {
        public override val listeners = mutableListOf<IResourceModelListener>()
    }

}