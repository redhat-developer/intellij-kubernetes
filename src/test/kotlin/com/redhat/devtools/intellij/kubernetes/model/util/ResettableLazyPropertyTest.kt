/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResettableLazyPropertyTest {

    private var initializerCalled = false
    private val value = "Garfield"
    private var character: ResettableLazyProperty<String> = ResettableLazyProperty {
        initializerCalled = true
        value
    }

    @Test
    fun `#get should call initializer`() {
        // given
        assertThat(initializerCalled).isFalse()
        // when
        character.get()
        // then
        assertThat(initializerCalled).isTrue()
    }

    @Test
    fun `#get should NOT call initializer if value was set`() {
        // given
        assertThat(initializerCalled).isFalse()
        character.set("lazy cat")
        // when
        character.get()
        // then
        assertThat(initializerCalled).isFalse()
    }

    @Test
    fun `#get should return value that was set`() {
        // given
        character.set("lazy cat")
        // when
        val value = character.get()
        // then
        assertThat(value).isEqualTo("lazy cat")
    }

    @Test
    fun `#get should call initializer again if property is resetted`() {
        // given
        character.get() // calls initializer
        assertThat(initializerCalled).isTrue()
        initializerCalled = false
        character.reset()
        // when
        character.get() // calls initializer again
        // then
        assertThat(initializerCalled).isTrue()
    }

}