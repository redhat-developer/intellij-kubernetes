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

import PresentationFactoryBuilder
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.common.validation.KubernetesResourceInfo
import com.redhat.devtools.intellij.kubernetes.balloon.StringInputBalloon
import com.redhat.devtools.intellij.kubernetes.editor.inlay.Base64Presentations.InlayPresentationsFactory
import com.redhat.devtools.intellij.kubernetes.editor.inlay.Base64Presentations.create
import com.redhat.devtools.intellij.kubernetes.editor.util.getBinaryData
import com.redhat.devtools.intellij.kubernetes.editor.util.getData
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import org.jetbrains.concurrency.runAsync
import java.awt.event.MouseEvent

/**
 * A factory that creates an [InlayPresentationsFactory] that creates an [InlayPresentationsFactory] for the given kubernetes resource.
 * The [InlayPresentationsFactory] creates [InlayPresentation]s for the properties in the resource.
 *
 * @see [create]
 */
object Base64Presentations {

	private const val SECRET_RESOURCE_KIND = "Secret"
	private const val CONFIGMAP_RESOURCE_KIND = "ConfigMap"

	fun create(content: PsiElement, info: KubernetesResourceInfo, sink: InlayHintsSink, editor: Editor): InlayPresentationsFactory? {
		return when {
			isKubernetesResource(SECRET_RESOURCE_KIND, info) -> {
				val data = getData(content) ?: return null
				StringPresentationsFactory(data, sink, editor)
			}

			isKubernetesResource(CONFIGMAP_RESOURCE_KIND, info) -> {
				val binaryData = getBinaryData(content) ?: return null
				BinaryPresentationsFactory(binaryData, sink, editor)
			}

			else -> null
		}
	}

	abstract class InlayPresentationsFactory(
		private val element: PsiElement,
		protected val sink: InlayHintsSink,
		protected val editor: Editor
	) {

		protected companion object {
			const val INLAY_HINT_MAX_WIDTH = 50
			const val WRAP_BASE64_STRING_AT = 76
		}

		fun create(): Collection<InlayPresentation> {
			return element.children.mapNotNull { child ->
				val adapter = Base64ValueAdapter(child)
				create(adapter)
			}
		}

		protected abstract fun create(adapter: Base64ValueAdapter): InlayPresentation?

	}

	class StringPresentationsFactory(element: PsiElement, sink: InlayHintsSink, editor: Editor)
		: InlayPresentationsFactory(element, sink, editor) {

		override fun create(adapter: Base64ValueAdapter): InlayPresentation? {
			val decoded = adapter.getDecoded() ?: return null
			val offset = adapter.getStartOffset() ?: return null
			val onClick = StringInputBalloon(
				decoded,
				onValidValue(adapter::set, editor.project),
				editor
			)::show
			val presentation = create(decoded, onClick, editor) ?: return null
			sink.addInlineElement(offset, false, presentation, false)
			return presentation
		}

		private fun create(text: String, onClick: (event: MouseEvent) -> Unit, editor: Editor): InlayPresentation? {
			val factory = PresentationFactoryBuilder.build(editor) ?: return null
			val trimmed = trimWithEllipsis(text, INLAY_HINT_MAX_WIDTH) ?: return null
			val textPresentation = factory.smallText(trimmed)
			val hoverPresentation = factory.referenceOnHover(textPresentation) { event, _ ->
				onClick.invoke(event)
			}
			val tooltipPresentation = factory.withTooltip("Click to change value", hoverPresentation)
			return factory.roundWithBackground(tooltipPresentation)
		}

		private fun onValidValue(setter: (value: String, wrapAt: Int) -> Unit, project: Project?)
				: (value: String) -> Unit {
			return { value ->
				runAsync {
					WriteCommandAction.runWriteCommandAction(project) {
						setter.invoke(value, WRAP_BASE64_STRING_AT)
					}
				}
			}
		}

	}

	class BinaryPresentationsFactory(element: PsiElement, sink: InlayHintsSink, editor: Editor)
		: InlayPresentationsFactory(element, sink, editor) {

		override fun create(adapter: Base64ValueAdapter): InlayPresentation? {
			val decoded = adapter.getDecodedBytes() ?: return null
			val offset = adapter.getStartOffset() ?: return null
			val presentation = create(decoded, editor) ?: return null
			sink.addInlineElement(offset, false, presentation, false)
			return presentation
		}

		private fun create(bytes: ByteArray, editor: Editor): InlayPresentation? {
			val factory = PresentationFactoryBuilder.build(editor) ?: return null
			val hex = toHexString(bytes) ?: return null
			val trimmed = trimWithEllipsis(hex, INLAY_HINT_MAX_WIDTH) ?: return null
			return factory.roundWithBackground(factory.smallText(trimmed))
		}

		private fun toHexString(bytes: ByteArray): String? {
			return try {
				bytes.joinToString(separator = " ") { byte ->
					Integer.toHexString(byte.toInt())
				}
			} catch (e: Exception) {
				null
			}
		}
	}
}