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

import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalExecutionConsole
import com.redhat.devtools.intellij.common.utils.ExecProcessHandler
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

open class TerminalTab(pod: Pod, model: IResourceModel, project: Project) :
    ConsoleTab<TerminalExecutionConsole, ExecWatch>(pod, model, project) {

    override fun startWatch(container: Container?, consoleView: TerminalExecutionConsole?): ExecWatch? {
        if (container == null
            || consoleView == null
        ) {
            return null
        }
        val watch = model.watchExec(container, pod, ContainerExecListener(container)) ?: return null
        this.watch.set(watch)
        val process = ExecWatchProcessAdapter(watch)
        val handler = ExecProcessHandler(process, "welcome to container ${container.name}\n", Charset.defaultCharset())
        consoleView.attachToProcess(handler)
        handler.startNotify()
        return watch
    }

    override fun createConsoleView(project: Project): TerminalExecutionConsole? {
        return TerminalExecutionConsole(project, null)
    }

    override fun getDisplayName(): String {
        return "Terminal: Pod ${pod.metadata.name}"
    }

    override fun dispose() {
        super.dispose()
        closeWatch()
    }

    private fun closeWatch() {
        val watch = watch.get() ?: return
        model.stopWatch(watch)
    }

    /**
     * An adapter that adapts an [ExecWatch] so that it can be accessed like a [Process]
     */
    inner class ExecWatchProcessAdapter(private val watch: ExecWatch) : Process() {

        @Override
        override fun getOutputStream(): OutputStream? {
            return watch.input
        }

        override fun getInputStream(): InputStream? {
            return watch.output
        }

        override fun getErrorStream(): InputStream? {
            return watch.error
        }

        override fun destroy() {
            watch.close()
        }

        override fun supportsNormalTermination(): Boolean {
            return true
        }

        override fun waitFor(): Int {
            return 0
        }

        override fun exitValue(): Int {
            return 0
        }
    }

    private inner class ContainerExecListener(private val container: Container): ExecListener {
        override fun onFailure(e: Throwable?, response: ExecListener.Response?) {
            showError(container, "Could not connect to container \"${container.name}\".")
        }

        override fun onClose(code: Int, reason: String?) {
            showError(container, "Connection to container \"${container.name}\" was closed.")
        }
    }
}
