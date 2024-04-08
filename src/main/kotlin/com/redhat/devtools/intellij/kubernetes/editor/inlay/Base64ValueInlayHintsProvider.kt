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
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.editor.util.getContent
import javax.swing.JComponent


internal class Base64ValueInlayHintsProvider : InlayHintsProvider<NoSettings> {

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

	override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
		val info = KubernetesResourceInfo.extractMeta(file) ?: return null
		return Collector(editor, info)
	}

	private class Collector(editor: Editor, private val info: KubernetesResourceInfo) : FactoryInlayHintsCollector(editor) {

		override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
			if (element !is PsiFile
				|| !element.isValid) {
				return true
			}
			val content = getContent(element) ?: return true
			val factory = Base64Presentations.create(content, info, sink, editor) ?: return true
			factory.create()
			return false
		}
	}
}