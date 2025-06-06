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
package com.redhat.devtools.intellij.kubernetes.editor.inlay.base64

import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import com.redhat.devtools.intellij.kubernetes.balloon.StringInputBalloon
import com.redhat.devtools.intellij.kubernetes.editor.inlay.base64.Base64Presentations.create
import com.redhat.devtools.intellij.kubernetes.editor.util.getBinaryData
import com.redhat.devtools.intellij.kubernetes.editor.util.getDataValue
import com.redhat.devtools.intellij.kubernetes.editor.util.getKey
import com.redhat.devtools.intellij.kubernetes.editor.util.isKubernetesResource
import com.redhat.devtools.intellij.kubernetes.model.util.trimWithEllipsis
import com.redhat.devtools.intellij.kubernetes.telemetry.TelemetryService
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

	fun create(
		element: PsiElement,
		info: KubernetesTypeInfo,
		sink: InlayHintsSink,
		editor: Editor,
		factory: PresentationFactory,
		/* for testing purposes */
		stringPresentationFactory: (element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit
			= { element, sink, editor, factory ->
				StringPresentationsFactory(element, sink, editor, factory).create()
			  },
		/* for testing purposes */
		binaryPresentationFactory: (element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory) -> Unit
			= { element, sink, editor, factory ->
				BinaryPresentationsFactory(element, sink, editor, factory).create()
			},
	) {
		when {
			isKubernetesResource(SECRET_RESOURCE_KIND, info) -> {
				val data = element.getDataValue() ?: return
				stringPresentationFactory.invoke(data, sink, editor, factory)
			}

			isKubernetesResource(CONFIGMAP_RESOURCE_KIND, info) -> {
				val binaryData = element.getBinaryData() ?: return
				binaryPresentationFactory.invoke(binaryData, sink, editor, factory)
			}
		}
	}

	abstract class InlayPresentationsFactory(
		private val element: PsiElement,
		protected val sink: InlayHintsSink,
		protected val editor: Editor,
		protected val factory: PresentationFactory
	) {

		protected companion object {
			const val INLAY_HINT_MAX_WIDTH = 50
			const val WRAP_BASE64_STRING_AT = 76
		}

		fun create(): Collection<InlayPresentation> {
			return element.children.mapNotNull { child ->
				create(Base64ValueAdapter(child))
			}
		}

		protected abstract fun create(adapter: Base64ValueAdapter): InlayPresentation?
	}

	class StringPresentationsFactory(element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory)
		: InlayPresentationsFactory(element, sink, editor, factory) {

		override fun create(adapter: Base64ValueAdapter): InlayPresentation? {
			val decoded = adapter.getDecoded() ?: return null
			val offset = adapter.getStartOffset() ?: return null
			val presentation = create(decoded, { event -> onClick(adapter.element, decoded, adapter, event) }, factory) ?: return null
			sink.addInlineElement(offset, false, presentation, false)
			return presentation
		}

		private fun create(text: String, onClick: (event: MouseEvent) -> Unit, factory: PresentationFactory): InlayPresentation? {
			val trimmed = trimWithEllipsis(text, INLAY_HINT_MAX_WIDTH) ?: return null
			return factory.roundWithBackground(
				factory.withTooltip(
					"Click to change value",
					factory.referenceOnHover(
						factory.smallText(trimmed)) { event, _ ->
							onClick.invoke(event)
					}
				)
			)
		}

		private fun onClick(element: PsiElement, decoded: String, adapter: Base64ValueAdapter, event: MouseEvent) {
			runAsync {
				TelemetryService.instance.action("${TelemetryService.NAME_PREFIX_EDITOR_HINT}base64Hint")
				.property(TelemetryService.PROP_PROPERTY_NAME, element.getKey()?.text)
				.send()
			}
			StringInputBalloon(
				decoded,
				onValidValue(adapter::set, editor.project),
				editor
			).show(event)
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

	class BinaryPresentationsFactory(element: PsiElement, sink: InlayHintsSink, editor: Editor, factory: PresentationFactory)
		: InlayPresentationsFactory(element, sink, editor, factory) {

		override fun create(adapter: Base64ValueAdapter): InlayPresentation? {
			val decoded = adapter.getDecodedBytes() ?: return null
			val offset = adapter.getStartOffset() ?: return null
			val presentation = create(decoded, editor) ?: return null
			sink.addInlineElement(offset, false, presentation, false)
			return presentation
		}

		private fun create(bytes: ByteArray, editor: Editor): InlayPresentation? {
			val factory = PresentationFactory(editor)
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