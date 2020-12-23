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
package com.redhat.devtools.intellij.kubernetes.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.progress.Task
import io.fabric8.kubernetes.api.model.HasMetadata
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure
import org.jetbrains.annotations.NotNull
import javax.swing.tree.DefaultMutableTreeNode
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.Descriptor

fun AnAction.getResourceModel(): IResourceModel? {
    return ServiceManager.getService(IResourceModel::class.java)
}

fun Any.getDescriptor(): Descriptor<*>? {
    return if (this is DefaultMutableTreeNode
        && this.userObject is Descriptor<*>) {
        return this.userObject as Descriptor<*>
    } else {
        null
    }
}

inline fun <reified T> Any.getElement(): T? {
    val element = getDescriptor()?.element
    return if (element is T) {
        element
    } else {
        null
    }
}

fun AnAction.run(title: String, canBeCancelled: Boolean, runnable: Progressive) {
    ProgressManager.getInstance().run(object :
        Task.Backgroundable(null, title, canBeCancelled){

        override fun run(@NotNull progress: ProgressIndicator) {
            runnable.run(progress)
        }
    })

}
