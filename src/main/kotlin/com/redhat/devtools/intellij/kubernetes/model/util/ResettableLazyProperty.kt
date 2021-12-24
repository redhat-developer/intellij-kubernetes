/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.util

/**
 * A writable and nullable property that is lazily initialized and may be resetted.
 * Kotlin `lazy` provides a read-only property that once initialized cannot be set to a different value or re-initialied.
 * `lateinit` may only be used for non-nullable properties.
 */
open class ResettableLazyProperty<T>(private val initializer: () -> T?) {

    private var initialized = false
    private var value: T? = null

    fun set(value: T) {
        this.initialized = true
        this.value = value
    }

    fun get(): T? {
        if (!initialized) {
            this.value = initializer.invoke()
            this.initialized = true
        }
        return value
    }

    fun reset() {
        this.initialized = false
    }
}