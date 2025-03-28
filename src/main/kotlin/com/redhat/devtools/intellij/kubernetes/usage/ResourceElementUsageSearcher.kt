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
package com.redhat.devtools.intellij.kubernetes.usage

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.redhat.devtools.intellij.kubernetes.editor.inlay.selector.SelectorFilter
import com.redhat.devtools.intellij.kubernetes.editor.util.PsiFiles
import com.redhat.devtools.intellij.kubernetes.editor.util.getAllElements
import com.redhat.devtools.intellij.kubernetes.editor.util.getLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource

class ResourceElementUsageSearcher: CustomUsageSearcher() {
    override fun processElementUsages(element: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
        ReadAction.run<Throwable> {
            if (!element.isValid
                || !element.isKubernetesResource()) {
                return@run
            }

            val file = element.containingFile
            if (file == null
                || !file.isValid) {
                return@run
            }

            val searchScope = options.searchScope
            if (searchScope.contains(file.virtualFile)) {
                    getAllMatching(element, file.fileType, element.project)
                    .forEach { matchingElement ->
                        val labelsElement = matchingElement.getLabels()?.parent // beginning of labels block/property
                        if (labelsElement != null) {
                            processor.process(UsageInfo2UsageAdapter(UsageInfo(labelsElement)))
                        }
                    }
            }
        }
    }

    private fun getAllMatching(element: PsiElement, fileType: FileType, project: Project): Collection<PsiElement> {
        val allElements = PsiFiles
            .getAll(fileType, project)
            .flatMap { projectFile -> projectFile.getAllElements() }
        return SelectorFilter(element)
            .filterMatching(allElements)
    }
}