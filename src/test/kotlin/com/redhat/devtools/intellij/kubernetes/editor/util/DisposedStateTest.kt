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
package com.redhat.devtools.intellij.kubernetes.editor.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DisposedStateTest {

    @Test
    fun `#isDisposed should return false initially`() {
        // given
        val state = DisposedState()
        // when
        val disposed = state.isDisposed()
        // then
        assertThat(disposed).isFalse()
    }

    @Test
    fun `#isDisposed should return true if it was set to true`() {
        // given
        val state = DisposedState()
        // when
        val disposed = state.setDisposed(true)
        // then
        assertThat(disposed).isTrue()
    }

    @Test
    fun `#setDisposed returns true if state was successfully changed`() {
        // given
        val state = DisposedState()
        val disposed = state.isDisposed()
        // when
        val changed = state.setDisposed(!disposed) // change state to opposite
        // then
        assertThat(changed).isTrue()
    }

    @Test
    fun `#setDisposed returns false if state was NOT changed`() {
        // given
        val state = DisposedState()
        val disposed = state.isDisposed()
        // when
        val changed = state.setDisposed(disposed) // set the same state again, no change
        // then
        assertThat(changed).isFalse()
    }

}