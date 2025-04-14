
/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor

import java.util.Objects

val FILTER_ALL = { _: EditorResource -> true }
val FILTER_TO_PUSH = { editorResource: EditorResource ->
    val state = editorResource.getState()
    state is Different
            && state.isPush()
}
val FILTER_ERROR = { editorResource: EditorResource ->
    editorResource.getState() is Error
}

val FILTER_PUSHED = { editorResource: EditorResource ->
    editorResource.getState() is Pushed
}

abstract class EditorResourceState {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash()
    }
}

class Error(val title: String, val message: String? = null): EditorResourceState() {
    constructor(title: String, e: Throwable) : this(title, e.message)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is Error
                && title == other.title
                && message == other.message
    }

    override fun hashCode(): Int {
        return Objects.hash(
            title,
            message
        )
    }
}

class Disposed: EditorResourceState() {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is Disposed
    }

    override fun hashCode(): Int {
        return Objects.hash()
    }
}

open class Identical: EditorResourceState()

abstract class Different(val exists: Boolean, val isOutdatedVersion: Boolean): EditorResourceState() {
    abstract fun isPush(): Boolean
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return other is Different
                && other.exists == exists
                && other.isOutdatedVersion == isOutdatedVersion
                && other.isPush() == isPush()
    }

    override fun hashCode(): Int {
        return Objects.hash(
            exists,
            isOutdatedVersion,
            isPush()
        )
    }
}

open class Modified(exists: Boolean, isOutdatedVersion: Boolean): Different(exists, isOutdatedVersion) {
    override fun isPush() = true
}

class DeletedOnCluster: Modified(false, false) {
    override fun isPush() = true
}

class Outdated: Different(true, true) {
    override fun isPush() = false
}

abstract class Pushed: Identical() {
    abstract val updated: Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return other is Pushed
                && other.updated == updated
    }

    override fun hashCode(): Int {
        return Objects.hashCode(updated)
    }
}

class Created(override val updated: Boolean = false) : Pushed()

class Updated(override val updated: Boolean = true): Pushed()

class Pulled: EditorResourceState()
