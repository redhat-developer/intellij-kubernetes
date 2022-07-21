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

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.Notification
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import org.jetbrains.concurrency.runAsync

abstract class ConsoleTab<T: ConsoleView?, W: Any?>(
    protected val pod: Pod,
    protected val model: IResourceModel,
    protected val project: Project
) : Disposable {

    protected val watches = emptyList<AtomicReference<W>>()
    protected val watch: AtomicReference<W?> = AtomicReference()
    private var consoles: CardLayoutPanel<Container,*,*>? = null

    fun createComponent(): JComponent {
        val containers = createContainersList(::onContainerSelected)
        val scrollPane = JBScrollPane(containers)
        val consoles = ConsolesPanel()
        this.consoles = consoles
        val splitPane = createSplitPanel(scrollPane, consoles)
        containers.selectedIndex = 0
        return createToolWindowPanel(splitPane)
    }

    private fun createToolWindowPanel(splitPane: JComponent): SimpleToolWindowPanel {
        return SimpleToolWindowPanel(false, true).apply {
            setContent(splitPane)
            revalidate()
            repaint()
        }
    }

    private fun onContainerSelected(container: Container) {
        consoles?.select(container, true)
    }

    private fun createSplitPanel(left: JComponent, right: JComponent): JComponent {
        return OnePixelSplitter(false, 0.15f)
            .apply {
                isShowDividerControls = true
                setHonorComponentsMinimumSize(true)
                firstComponent = left
                secondComponent = right
            }
    }

    private fun createContainersList(onSelected: (container: Container) -> Unit): JBList<*> {
        return JBList<ContainerLabel>().apply {
            addListSelectionListener { onSelected.invoke(selectedValue.container) }
            val listModel = CollectionListModel<ContainerLabel>()
            listModel.add(pod.spec.containers
                .map { container -> ContainerLabel(container) })
            model = listModel
            selectionModel = SingleSelectionModel()
        }
    }


    abstract fun createConsoleView(project: Project): T?

    abstract fun getDisplayName(): String

    protected fun createMessageComponent(message: String): JComponent {
        return JLabel(message).apply {
            isEnabled = false
            horizontalAlignment = JLabel.CENTER
        }
    }

    protected abstract fun startWatch(container: Container?, consoleView: T?): W?

    private class ContainerLabel(val container: Container) {
        override fun toString(): String {
            return "  " + container.name // spaces to create left indent (insets/border dont affect selection insets)
        }
    }

    private inner class ConsolesPanel: CardLayoutPanel<Container, (project: Project) -> T?, JComponent>() {
        override fun prepare(key: Container?): (project: Project) -> T? {
            return ::createConsoleView
        }

        override fun create(consoleViewFactory: (project: Project) -> T?): JComponent? {
            val view = consoleViewFactory.invoke(project)
            runAsync {
                val container = key
                try {
                    startWatch(container, view)
                } catch (e: ResourceException) {
                    logger<TerminalTab>().debug("Could not watch container ${container?.name ?: ""} ", e)
                    Notification().error("Console error", "Could connect to container ${container?.name ?: ""}")
                    null
                }
            }
            return view?.component
        }
    }
}
