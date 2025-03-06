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
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.editor.inlay.base64.Base64Presentations
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
					create(element, sink, editor)
					false
				}
				is JsonFile -> {
					create(element, sink, editor)
					false
				}
				else -> true
			}
		}

		private fun create(file: YAMLFile, sink: InlayHintsSink, editor: Editor) {
			return ReadAction.run<Exception> {
				file.documents.forEach { document ->
					val info = KubernetesTypeInfo.create(document) ?: return@forEach
					val element = document.topLevelValue ?: return@forEach
					Base64Presentations.create(element, info, sink, editor)?.create()
				}
			}
		}

		private fun create(file: JsonFile, sink: InlayHintsSink, editor: Editor) {
			return ReadAction.run<Exception> {
				val info = KubernetesTypeInfo.create(file) ?: return@run
				val element = file.topLevelValue ?: return@run
				Base64Presentations.create(element, info, sink, editor)?.create()
			}
		}

	}
}