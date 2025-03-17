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
import java.awt.event.MouseEvent
import javax.swing.Icon


object SelectorPresentations {

    private val selectorIcon = IconManager.getInstance().getIcon("icons/selector.svg", javaClass)
    private val labelIcon = IconManager.getInstance().getIcon("icons/label.svg", javaClass)

    fun createForSelector(
        element: PsiElement,
        allElements: List<PsiElement>,
        filter: LabelsFilter = LabelsFilter(element),
        sink: InlayHintsSink,
        editor: Editor,
        factory: PresentationFactory
    ): Collection<InlayPresentation> {
        val matchingElements = allElements
            .filter(filter::isAccepted)
        if (matchingElements.isEmpty()) {
            return emptyList()
        }
        val selectorKeyValue = element.getSelector()?.parent
            ?: return emptyList()

        return create(
            selectorKeyValue,
            "${matchingElements.size} matching",
            "Click to see matching labels",
            selectorIcon,
            editor,
            sink,
            factory
        )
    }

    fun createForLabel(
        element: PsiElement,
        allElements: List<PsiElement>,
        filter: SelectorsFilter = SelectorsFilter(element),
        sink: InlayHintsSink,
        editor: Editor,
        factory: PresentationFactory
    ): Collection<InlayPresentation> {
        val matchingElements = allElements
            .filter(filter::isAccepted)
        if (matchingElements.isEmpty()) {
            return emptyList()
        }
        val labelsKeyValue = element.getLabels()?.parent
            ?: return emptyList()

        return create(
            labelsKeyValue,
            "${matchingElements.size} matching",
            "Click to see matching selectors",
            labelIcon,
            editor,
            sink,
            factory
        )
    }

    private fun create(element: PsiElement,
                       text: String,
                       toolTip: String,
                       icon: Icon,
                       editor: Editor,
                       sink: InlayHintsSink,
                       factory: PresentationFactory
    ): Collection<InlayPresentation> {
        val offset = element.getKey()?.textRange?.endOffset // to the right of the key
            ?: return emptyList()

        val textPresentation = createText(element, text, toolTip, editor, factory)
        sink.addInlineElement(offset, true, textPresentation, true)

        val iconPresentation = createIcon(element, factory, editor, icon)
        sink.addInlineElement(offset, true, iconPresentation, true)

        return listOf(textPresentation, iconPresentation)
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

    private fun onClick(editor: Editor, hintedKeyValue: PsiElement ):
                (event: MouseEvent, point: java.awt.Point) -> Unit {

        return { event, point ->
            //GotoDeclarationAction.startFindUsages(editor, element.project, element, RelativePoint(event))
            ShowUsagesAction.startFindUsages(hintedKeyValue, RelativePoint(event), editor)
        }
    }
}