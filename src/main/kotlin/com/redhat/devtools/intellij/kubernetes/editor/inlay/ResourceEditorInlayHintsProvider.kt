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
@file:Suppress("UnstableApiUsage")

package com.redhat.devtools.intellij.kubernetes.editor.inlay

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.inlay.base64.Base64Presentations
import com.redhat.devtools.intellij.kubernetes.editor.inlay.selector.SelectorPresentations
import com.redhat.devtools.intellij.kubernetes.editor.util.PsiElements
import org.jetbrains.yaml.psi.YAMLFile
import javax.swing.JComponent


internal class ResourceEditorInlayHintsProvider : InlayHintsProvider<NoSettings> {

	override val key: SettingsKey<NoSettings> = SettingsKey("KubernetesResource.hints")
	override val name: String = "Kubernetes"
	override val previewText: String = "Preview"

	override fun createSettings(): NoSettings {
		return NoSettings()
	}

	override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
		return object : ImmediateConfigurable {
			override fun createComponent(listener: ChangeListener): JComponent = panel {}

			override val mainCheckboxText: String = "Show hints for:"

			override val cases: List<ImmediateConfigurable.Case> = emptyList()
		}
	}

	override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
		return Collector(editor)
	}

	private class Collector(editor: Editor) : FactoryInlayHintsCollector(editor) {

		override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
			if (!element.isValid) {
				return true
			}
			return when(element) {
				is YAMLFile -> {
					create(element, sink, editor, factory)
					false
				}
				is JsonFile -> {
					create(element, sink, editor, factory)
					false
				}
				else -> true
			}
		}

		private fun create(file: YAMLFile, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) {
			return ReadAction.run<Exception> {
				file.documents
					.mapNotNull { document -> document.topLevelValue }
					.forEach { element ->
						createPresentations(element, sink, editor, factory)
					}
			}
		}

		private fun create(file: JsonFile, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) {
			return ReadAction.run<Exception> {
				file.allTopLevelValues.forEach { element ->
					createPresentations(element, sink, editor, factory)
				}
			}
		}

		private fun createPresentations(element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) {
			val info = KubernetesTypeInfo.create(element) ?: return
			Base64Presentations.create(element, info, sink, editor, factory)

			val fileType = editor.virtualFile?.fileType ?: return
			val project = editor.project ?: return
			val allElements = PsiElements.getAllNoExclusions(fileType, project)
			SelectorPresentations.createForSelector(element, allElements, sink, editor, factory)
			SelectorPresentations.createForAllLabels(element, allElements, sink, editor, factory)
		}
	}
}