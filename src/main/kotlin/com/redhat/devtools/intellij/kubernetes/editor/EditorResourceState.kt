
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

abstract class EditorResourceState

class Error(val title: String, val message: String? = null): EditorResourceState() {
    constructor(title: String, e: Throwable) : this(title, e.message)
}

open class Identical: EditorResourceState()

abstract class Different(val exists: Boolean, val isOutdatedVersion: Boolean): EditorResourceState() {
    abstract fun isPush(): Boolean
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
}

class Created: Pushed() {
    override val updated = false
}
class Updated: Pushed() {
    override val updated = true
}

class Pulled: EditorResourceState()
