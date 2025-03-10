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
package com.redhat.devtools.intellij.kubernetes.editor.inlay.selector

import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.awt.RelativePoint
import com.redhat.devtools.intellij.kubernetes.editor.util.getKey
import com.redhat.devtools.intellij.kubernetes.editor.util.getLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.getTemplate
import com.redhat.devtools.intellij.kubernetes.editor.util.hasTemplate
import com.redhat.devtools.intellij.kubernetes.usage.LabelsFilter
import com.redhat.devtools.intellij.kubernetes.usage.SelectorsFilter
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon


object SelectorPresentations {

    private val selectorIcon = IconManager.getInstance().getIcon("icons/selector.svg", javaClass)
    private val labelIcon = IconManager.getInstance().getIcon("icons/label.svg", javaClass)

    fun createForSelector(
        element: PsiElement,
        allElements: List<PsiElement>,
        sink: InlayHintsSink,
        editor: Editor,
        factory: PresentationFactory
    ) {
        val filter = LabelsFilter(element)
        val matchingElements = allElements
            .filter(filter::isAccepted)
        val selectorAttribute = element.getSelector()?.parent
            ?: return

        create(
            selectorAttribute,
            "${matchingElements.size} matching",
            "Click to see matching labels",
            selectorIcon,
            editor,
            sink,
            factory
        )
    }

    fun createForAllLabels(
        element: PsiElement,
        allElements: List<PsiElement>,
        sink: InlayHintsSink,
        editor: Editor,
        factory: PresentationFactory
    ) {
        createForLabels(element, element.getLabels(), allElements, sink, editor, factory)
        if (element.hasTemplate()) {
            createForLabels(element, element.getTemplate()?.getLabels(), allElements, sink, editor, factory)
        }
    }

    private fun createForLabels(
        resource: PsiElement,
        labels: PsiElement?,
        allElements: List<PsiElement>,
        sink: InlayHintsSink,
        editor: Editor,
        factory: PresentationFactory
    ) {
        val labelsAttribute = labels?.parent
            ?: return
        val filter = SelectorsFilter(resource)
        val matchingElements = allElements
            .filter(filter::isAccepted)
        create(
            labelsAttribute,
            "${matchingElements.size} matching",
            "Click to see matching selectors",
            labelIcon,
            editor,
            sink,
            factory
        )
    }

    private fun create(
        element: PsiElement,
        text: String,
        toolTip: String,
        icon: Icon,
        editor: Editor,
        sink: InlayHintsSink,
        factory: PresentationFactory
    ) {
        val offset = element.getKey()?.textRange?.endOffset // to the right of the key
            ?: return

        val textPresentation = createText(element, text, toolTip, editor, factory)
        sink.addInlineElement(offset, true, textPresentation, true)

        val iconPresentation = createIcon(element, factory, editor, icon)
        sink.addInlineElement(offset, true, iconPresentation, true)
    }

    private fun createText(
        element: PsiElement,
        text: String,
        toolTip: String,
        editor: Editor,
        factory: PresentationFactory
    ): InlayPresentation {
        return factory.withTooltip(
            toolTip,
            factory.referenceOnHover(
                factory.roundWithBackground(
                    factory.text(
                        text
                    )
                ),
                onClick(editor, element)
            )
        )
    }

    private fun createIcon(
        selector: PsiElement,
        factory: PresentationFactory,
        editor: Editor,
        icon: Icon
    ): InlayPresentation {
        val iconPresentation = factory.referenceOnHover(
            factory.roundWithBackground(
                factory.smallScaledIcon(icon)
            ),
            onClick(editor, selector)
        )
        return iconPresentation
    }

    private fun onClick(editor: Editor, hintedKeyValue: PsiElement):
                (event: MouseEvent, _: Point) -> Unit {

        return { event, point ->
            val project = editor.project
            if (project != null) {
                ShowUsagesAction.startFindUsages(hintedKeyValue, RelativePoint(event), editor)
            }
        }
    }


}

