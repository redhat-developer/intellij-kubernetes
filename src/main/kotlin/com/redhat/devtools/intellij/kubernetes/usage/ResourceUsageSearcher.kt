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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiElementFilter
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.redhat.devtools.intellij.kubernetes.editor.inlay.selector.LabelsFilter
import com.redhat.devtools.intellij.kubernetes.editor.inlay.selector.SelectorsFilter
import com.redhat.devtools.intellij.kubernetes.editor.util.PsiElements
import com.redhat.devtools.intellij.kubernetes.editor.util.getLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.getResource
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelector
import com.redhat.devtools.intellij.kubernetes.editor.util.isLabels
import com.redhat.devtools.intellij.kubernetes.editor.util.isSelector

class ResourceUsageSearcher: CustomUsageSearcher() {

    override fun processElementUsages(searchParameter: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
        ReadAction.run<Throwable> {
            if (!searchParameter.isValid) {
                return@run
            }

            val file = searchParameter.containingFile
            if (file == null
                || !file.isValid) {
                return@run
            }

            val searchScope = options.searchScope
            if (searchScope.contains(file.virtualFile)) {
                getAllMatching(searchParameter)
                    .forEach { matchingElement ->
                        val searchResult = getSearchResult(searchParameter, matchingElement)
                        if (searchResult != null) {
                            processor.process(
                                UsageInfo2UsageAdapter(UsageInfo(searchResult))
                            )
                        }
                    }
            }
        }
    }

    private fun getAllMatching(searchParameter: PsiElement): Collection<PsiElement> {
        val fileType = searchParameter.containingFile.fileType
        val project = searchParameter.project
        val filter = getFilter(searchParameter) ?: return emptyList()
        return PsiElements.getAll(fileType, project)
            .filter(filter::isAccepted)
    }

    private fun getFilter(searchParameter: PsiElement): PsiElementFilter? {
        val resource = searchParameter.getResource() ?: return null
        return when {
            searchParameter.isSelector() ->
                LabelsFilter(resource)

            searchParameter.isLabels() ->
                SelectorsFilter(resource)

            else ->
                null
        }
    }

    private fun getSearchResult(searchParameter: PsiElement, matchingElement: PsiElement): PsiElement? {
        return when {
            searchParameter.isSelector() ->
                matchingElement.getLabels()?.parent // beginning of labels block/property
            searchParameter.isLabels() ->
                matchingElement.getSelector()?.parent // beginning of selector block/property
            else ->
                null
        }
    }
}