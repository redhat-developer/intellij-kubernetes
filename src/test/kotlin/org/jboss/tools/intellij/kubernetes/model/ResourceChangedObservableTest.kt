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

import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.ResourceChangeObservable.ResourceChangeListener
import org.junit.Before
import org.junit.Test

class ResourceChangedObservableTest {

    private val observable = ResourceChangeObservable()
    private val resource = Any()
    private val listener = object: ResourceChangeListener {

        var removedResources = mutableListOf<Any>()
        var addedResources = mutableListOf<Any>()
        var modifiedResources = mutableListOf<Any>()

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
    fun `#fireRemoved should notify removed resource`() {
        // given
        // when
        observable.fireRemoved(resource)
        // then
        assertThat(listener.removedResources.take(1)).containsExactly(resource)
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources).isEmpty()
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
    }
}