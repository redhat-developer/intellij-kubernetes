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

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface to manage a disposed state.
 * The state can be checked and set.
 * The state is thread safe.
 */
interface IDisposedState {

    /**
     * @return true if the state is disposed, false otherwise
     */
    fun isDisposed(): Boolean

    /**
     * Set the disposed state.
     * @param disposed the new disposed state
     * @return true if the state was changed, false otherwise
     */
    fun setDisposed(disposed: Boolean): Boolean
}

class DisposedState: IDisposedState {
    private val disposed = AtomicBoolean(false)

    override fun isDisposed(): Boolean {
        return disposed.get()
    }

    override fun setDisposed(disposed: Boolean): Boolean {
        return this.disposed.compareAndSet(!disposed, disposed)
    }

}