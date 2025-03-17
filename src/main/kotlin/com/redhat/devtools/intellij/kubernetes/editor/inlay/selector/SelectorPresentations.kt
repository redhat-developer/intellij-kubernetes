/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.awt.RelativePoint
import com.redhat.devtools.intellij.kubernetes.editor.util.PsiFiles
import com.redhat.devtools.intellij.kubernetes.editor.util.getAllElements
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelectorKeyElement
import java.awt.event.MouseEvent


object SelectorPresentations {

    private val selectorIcon = IconManager.getInstance().getIcon("icons/selector.svg", javaClass)

    fun create(
        element: PsiElement,
        sink: InlayHintsSink,
        editor: Editor,
        filter: SelectorFilter = SelectorFilter(element)
    ): Collection<InlayPresentation> {
        val project = editor.project ?: return emptyList()
        val fileType = editor.virtualFile.fileType
        val matchingElements = PsiFiles
            .getAll(fileType, project)
            .flatMap { file -> file.getAllElements() }
            .filter(filter::isMatching)
        if (matchingElements.isEmpty()) {
            return emptyList()
        }
        val factory = PresentationFactory(editor)

        val offset = element.getSelectorKeyElement()?.textRange?.endOffset
            ?: return emptyList()

        val presentation = createText(factory, matchingElements, editor, element)
        sink.addInlineElement(offset, true, presentation, true)

        val iconPresentation = createIcon(factory, editor, element)
        sink.addInlineElement(offset, true, iconPresentation, true)

        return listOf(presentation, iconPresentation)
    }

    private fun createText(
        factory: PresentationFactory,
        matchingElements: List<PsiElement>,
        editor: Editor,
        element: PsiElement
    ): InlayPresentation {
        return factory.withTooltip(
            "Click to see matching resources",
            factory.referenceOnHover(
                factory.roundWithBackground(
                    factory.text(
                        "${matchingElements.size} matching"
                    )
                ), onClick(editor, element)
            )
        )
    }

    private fun createIcon(
        factory: PresentationFactory,
        editor: Editor,
        element: PsiElement
    ): InlayPresentation {
        val iconPresentation = factory.referenceOnHover(
            factory.roundWithBackground(
                factory.smallScaledIcon(selectorIcon)
            ),
            onClick(editor, element)
        )
        return iconPresentation
    }

    private fun onClick(
        editor: Editor,
        element: PsiElement
    ): (event: MouseEvent, point: java.awt.Point) -> Unit {
        return { event, point ->
            GotoDeclarationAction.startFindUsages(editor, element.project, element, RelativePoint(event))
        }
    }

}