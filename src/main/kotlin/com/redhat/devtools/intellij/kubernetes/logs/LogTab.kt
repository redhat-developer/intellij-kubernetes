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
package com.redhat.devtools.intellij.kubernetes.logs

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.terminal.TerminalExecutionConsole
import com.redhat.devtools.intellij.common.utils.UIHelper.executeInUI
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.awt.BorderLayout
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.concurrency.runAsync

open class LogTab(private val container: Container, private val pod: Pod, private val model: IResourceModel, private val project: Project): Disposable {

    private var terminalPanel: JPanel? = null
    private var terminal: TerminalExecutionConsole? = null
    private var watch: AtomicReference<LogWatch?> = AtomicReference()

    fun watchLog() {
        val terminal = this.terminal
        if (terminal == null) {
            showInTerminal(createMessageComponent("Could not show log, terminal not ready."))
            return
        }
        runAsync {
            try {
                watch.set(
                    model.watchLog(container, pod, TerminalOutputStream(terminal))
                )
            } catch (e: Exception) {
                logger<LogTab>().warn("Could not read logs for container ${container.name} of pod ${pod.metadata.name}", e.cause)
                showInTerminal(createMessageComponent("Could not read logs for container ${container.name} of pod ${pod.metadata.name}: ${toMessage(e.cause)}."))
            }
        }
    }

    fun getComponent(): JComponent {
        return createComponents()
    }

    private fun createComponents(): SimpleToolWindowPanel {
        val panel = JPanel(BorderLayout())
        this.terminalPanel = panel
        val toolWindowPanel = createToolWindowPanel(panel)
        val terminal = createTerminal(project, panel)
        this.terminal = terminal
        showInTerminal(createMessageComponent("Loading logs..."))
        showInTerminal(terminal.component)
        return toolWindowPanel
    }

    private fun createToolWindowPanel(child: JPanel): SimpleToolWindowPanel {
        return SimpleToolWindowPanel(false, true).apply {
            setContent(child)
            revalidate()
        }
    }

    private fun createMessageComponent(message: String): JComponent {
        return JLabel(message).apply {
            isEnabled = false
            horizontalAlignment = JLabel.CENTER
        }
    }

    private fun createTerminal(project: Project, parent: JPanel): TerminalExecutionConsole {
        val processHandler: ProcessHandler = object: ProcessHandler() {

            override fun getProcessInput(): OutputStream? {
                return null
            }

            override fun destroyProcessImpl() {
                // not supported
            }

            override fun detachProcessImpl() {
                // not supported
            }

            override fun detachIsDefault(): Boolean {
                return false
            }
        }
// val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
// val contentFactory = ContentFactory.SERVICE.getInstance()
        val terminal = TerminalExecutionConsole(project, processHandler)
        parent.add(terminal.component, BorderLayout.CENTER)
        processHandler.startNotify()
        return terminal
    }

    private fun showInTerminal(component: JComponent?) {
        val terminalPanel = this.terminalPanel
        if (component == null
            || terminalPanel == null) {
            return
        }
        executeInUI {
            terminalPanel.apply {
                removeAll()
                add(component, BorderLayout.CENTER)
                revalidate()
                repaint()
            }
        }
    }

    fun getDisplayName(): String {
        return "${pod.kind} '${pod.metadata.name}'"
    }

    override fun dispose() {
        watch.get()?.close()
    }
}
