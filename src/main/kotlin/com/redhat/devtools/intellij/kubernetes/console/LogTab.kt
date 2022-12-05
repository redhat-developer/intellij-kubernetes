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
package com.redhat.devtools.intellij.kubernetes.console

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.dsl.LogWatch

open class LogTab(pod: Pod, model: IResourceModel, project: Project) :
    ConsoleTab<ConsoleView, LogWatch>(pod, model, project) {

    override fun startWatch(container: Container?, consoleView: ConsoleView?): LogWatch? {
        if (container == null
            || consoleView == null) {
            return null
        }
        val watch = model.watchLog(
            container,
            pod, FailureCallbackOutputStream(
                onFailureDetected(container),
                consoleView
            )
        )
        this.watch.set(watch)
        return watch
    }

    private fun onFailureDetected(container: Container): (message: String) -> Unit {
        return { message ->
            val e = ResourceException(message)
            runInEdt {
                showError(container, "Could not connect to container \"${container.name}\".", e)
            }
        }
    }

    override fun createConsoleView(project: Project): ConsoleView {
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        builder.setViewer(true)
        return builder.console
    }

    override fun getDisplayName(): String {
        return "Log: Pod ${pod.metadata.name}"
    }

    override fun dispose() {
        super.dispose()
        closeWatch()
    }

    private fun closeWatch() {
        val watch = watch.get() ?: return
        model.stopWatch(watch)
    }
}
