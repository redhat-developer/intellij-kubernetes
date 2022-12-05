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
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.redhat.devtools.intellij.kubernetes.balloon.ErrorBalloon
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.getStatus
import com.redhat.devtools.intellij.kubernetes.model.util.isRunning
import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Pod
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import org.jetbrains.concurrency.runAsync

abstract class ConsoleTab<T : ConsoleView, W : Any?>(
    protected val pod: Pod,
    protected val model: IResourceModel,
    protected val project: Project
) : Disposable {

    protected val watches = emptyList<AtomicReference<W>>()
    protected val watch: AtomicReference<W?> = AtomicReference()
    private var consoles: CardLayoutPanel<Container, Container, ConsoleOrErrorPanel>? = null

    fun createComponent(): JComponent {
        val containers = createContainersList(::onContainerSelected)
        val scrollPane = JBScrollPane(containers)
        val consoles = ConsolesPanel()
        this.consoles = consoles
        val toSelect = indexOfFirstRunningContainer(containers.model, pod)
        containers.selectedIndex = toSelect
        val splitPane = createSplitPanel(scrollPane, consoles)
        return createToolWindowPanel(splitPane)
    }

    private fun indexOfFirstRunningContainer(model: ListModel<ContainerLabelAdapter>, pod: Pod): Int {
        var i = 0
        do {
            val container = model.getElementAt(i).container
            if (isRunning(getStatus(container, pod.status))) {
                return i
            }
        } while (++i < model.size)
        return 0
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
        return OnePixelSplitter(false, 0.15f).apply {
            isShowDividerControls = true
            setHonorComponentsMinimumSize(true)
            firstComponent = left
            secondComponent = right
        }
    }

    private fun createContainersList(onSelected: (container: Container) -> Unit): JBList<ContainerLabelAdapter> {
        return JBList<ContainerLabelAdapter>().apply {
            addListSelectionListener { onSelected.invoke(selectedValue.container) }
            val listModel = CollectionListModel<ContainerLabelAdapter>()
            listModel.add(pod.spec.initContainers
                .map { container -> InitContainerLabelAdapter(container) })
            listModel.add(pod.spec.containers
                .map { container -> ContainerLabelAdapter(container) })
            model = listModel
            selectionModel = SingleSelectionModel()
        }
    }

    override fun dispose() {
        consoles?.dispose()
    }


    abstract fun createConsoleView(project: Project): T?

    abstract fun getDisplayName(): String

    protected abstract fun startWatch(container: Container?, consoleView: T?): W?


    protected fun showError(container: Container, message: String, e: Throwable? = null) {
        val consoleOrErrorPanel = consoles?.getValue(container, false) ?: return
        consoleOrErrorPanel.showError(message, e)
    }

    private class InitContainerLabelAdapter(container: Container): ContainerLabelAdapter(container) {

        override fun toString(): String {
            return "${super.toString()} (init)"
        }
    }

    private open class ContainerLabelAdapter(val container: Container) {
        override fun toString(): String {
            // workaround: spaces to create left indent (insets/border don't affect selection insets)
            return "  ${container.name}"
        }
    }

    /**
     * A card-panel that displays panels for each container.
     */
    private inner class ConsolesPanel : CardLayoutPanel<Container, Container, ConsoleOrErrorPanel>() {

        override fun prepare(container: Container): Container {
            return container
        }

        override fun create(container: Container): ConsoleOrErrorPanel {
            return ConsoleOrErrorPanel(container)
        }

        override fun dispose() {
            removeAll()
        }

        override fun dispose(key: Container?, value: ConsoleOrErrorPanel?) {
            value?.dispose()
        }
    }

    /**
     * A card-Panel that either displays the console or an error.
     */
    private inner class ConsoleOrErrorPanel(private val container: Container): SimpleCardLayoutPanel<JComponent>() {

        private val NAME_VIEW_CONSOLE = "console"
        private val NAME_VIEW_ERROR = "error"

        private val consoleView by lazy {
            val view = createConsoleView(project)
            if (view != null) {
                asyncStartWatch(view)
            }
            view
        }

        private val errorView by lazy {
            ErrorView(this)
        }

        init {
            val consoleView = consoleView
            if (consoleView != null) {
                add(consoleView.component, NAME_VIEW_CONSOLE)
            }
            add(errorView.component, NAME_VIEW_ERROR)
            showConsole()
        }

        fun showConsole() {
            show(NAME_VIEW_CONSOLE)
        }

        fun showError(message: String, e: Throwable?) {
            errorView.setError(message, e) {
                showConsole()
                asyncStartWatch(consoleView)
            }
            show(NAME_VIEW_ERROR)
        }

        override fun dispose() {
            consoleView?.dispose()
        }

        private fun asyncStartWatch(consoleView: T?) {
            if (consoleView == null) {
                return
            }
            runAsync {
                try {
                    startWatch(container, consoleView)
                } catch (e: ResourceException) {
                    logger<TerminalTab>().warn(e)
                    showError("Could not connect to container \"${container.name}\".", e)
                }
            }
        }

    }

    private class ErrorView(private val parent: Disposable) {
        private val errorLabel: HyperlinkLabel by lazy {
            HyperlinkLabel().apply {
                setIcon(AllIcons.General.Error)
                addHyperlinkListener {
                    errorDetailsListener?.invoke()
                }
            }
        }

        private val reconnectLabel: HyperlinkLabel by lazy {
            HyperlinkLabel().apply {
                setHtmlText("<a>Reconnect.</a>")
                addHyperlinkListener {
                    reconnectListener?.invoke()
                }
            }
        }

        val component by lazy {
            JPanel().apply {
                layout = GridBagLayout()
                add(JPanel().apply {
                    layout = FlowLayout()
                    add(errorLabel)
                    add(reconnectLabel)
                })
            }
        }

        private var reconnectListener: (() -> Unit)? = null
        private var errorDetailsListener: (() -> Unit)? = null

        fun setError(message: String, e: Throwable?, listener: () -> Unit) {
            errorLabel.setHtmlText("$message <a>Details.</a>")
            this.errorDetailsListener = {
                val balloon = ErrorBalloon.create(toMessage(e), component)
                ErrorBalloon.showAbove(balloon, errorLabel)
                Disposer.register(parent, balloon)
            }
            this.reconnectListener = listener
        }

    }
}
