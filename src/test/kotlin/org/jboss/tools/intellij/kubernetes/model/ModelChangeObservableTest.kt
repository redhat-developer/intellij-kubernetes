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
package org.jboss.tools.intellij.kubernetes.model

import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.api.model.Namespace
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.ModelChangeObservable.IResourceChangeListener
import org.jboss.tools.intellij.kubernetes.model.mocks.Mocks.resource
import org.junit.Before
import org.junit.Test

class ModelChangeObservableTest {

    private val observable = ModelChangeObservable()
    private val resource = resource<Namespace>("smurfette namespace")
    private val listener = object: IResourceChangeListener {

        var currentNamespace: String? = null
        val removedResources = mutableListOf<Any>()
        val addedResources = mutableListOf<Any>()
        val modifiedResources = mutableListOf<Any>()

        override fun currentNamespace(namespace: String?) {
            currentNamespace = namespace
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
    fun `#fireRemoved should notify removed resource`() {
        // given
        // when
        observable.fireRemoved(resource)
        // then
        assertThat(listener.removedResources.take(1)).containsExactly(resource)
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources).isEmpty()
        assertThat(listener.currentNamespace).isNull()
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
        assertThat(listener.currentNamespace).isNull()
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
        assertThat(listener.currentNamespace).isNull()
    }

    @Test
    fun `#fireCurrentNamespace should notify current namespace`() {
        // given
        // when
        observable.fireCurrentNamespace(resource.metadata.name)
        // then
        assertThat(listener.removedResources).isEmpty()
        assertThat(listener.addedResources).isEmpty()
        assertThat(listener.modifiedResources).isEmpty()
        assertThat(listener.currentNamespace).isEqualTo(resource.metadata.name)
    }

}