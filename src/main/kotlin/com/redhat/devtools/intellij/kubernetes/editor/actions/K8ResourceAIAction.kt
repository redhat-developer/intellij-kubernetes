package com.redhat.devtools.intellij.kubernetes.editor.actions

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.components.BorderLayoutPanel
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditorFactory
import com.redhat.devtools.intellij.kubernetes.editor.util.getSelectedFileEditor
import io.fabric8.kubernetes.client.utils.Serialization
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JTextField

class K8ResourceAIAction : AnAction() {

	private val loadingIcon: JBLabel = JBLabel(AnimatedIcon.Default.INSTANCE)
	private lateinit var editor: Editor
	private var document: Document = EditorFactory.getInstance().createDocument(CharArray(0))
	private lateinit var fileEditor: FileEditor

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		fileEditor = getSelectedFileEditor(project) ?: return
		val balloon = createPopup(project)
		val toolbarIcon = e.inputEvent.component
		balloon.show(
			PositionTracker.Static(
				RelativePoint(toolbarIcon, Point(toolbarIcon.width / 2, toolbarIcon.height))
			),
			Balloon.Position.below
		)
	}

	private fun createPopup(project: Project): Balloon {
		val balloonPanel = BorderLayoutPanel()

		val textField = JBTextField(50)
		stopLoading()
		val textFieldPanel = BorderLayoutPanel().apply {
			addToLeft(textField)
			add(BorderLayoutPanel().addToCenter(loadingIcon))
			border = JBUI.Borders.emptyBottom(4)
		}
		balloonPanel.addToTop(textFieldPanel)

		val factory = EditorFactory.getInstance()
		editor = factory.createEditor(document, project, EditorKind.DIFF)
		balloonPanel.addToCenter(editor.component.apply {
			preferredSize = Dimension(600, 600)
		})

		val applyButton = JButton("Apply")
		val buttonPanel = BorderLayoutPanel().apply {
			addToRight(applyButton)
			border = JBUI.Borders.empty(4, 4, 2, 4)
		}
		balloonPanel.addToBottom(buttonPanel)

		val balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonPanel)
			.setFillColor(balloonPanel.background)
			.setBorderColor(balloonPanel.background)
			.setBorderInsets(JBInsets(4, 0, 0, 0))
			.setHideOnClickOutside(true)
			.setHideOnAction(false) // allow user to Ctrl+A & Ctrl+C
			.createBalloon()
		textField.addKeyListener(onKeyPressed(textField, document, editor, balloon))
		applyButton.addMouseListener(onApply(document, fileEditor, balloon, project))
		return balloon
	}

	private fun onKeyPressed(text: JTextField, document: Document, editor: Editor, balloon: Balloon): KeyListener {
		return object : KeyAdapter() {
			override fun keyReleased(e: KeyEvent) {
				when {
					KeyEvent.VK_ESCAPE == e.keyCode ->
						balloon.hide()

					KeyEvent.VK_ENTER == e.keyCode -> {
						startLoading()
						query(text.text)
							.thenAccept { jsonYaml ->
								stopLoading()
								replaceDocument(jsonYaml, document, editor.project)
							}
					}
				}
			}
		}
	}

	private fun replaceDocument(jsonYaml: String?, document: Document, project: Project?) {
		if (jsonYaml == null) {
			return
		}
		WriteCommandAction.runWriteCommandAction(project) {
			document.replaceString(0, document.textLength, jsonYaml)
		}
	}

	private fun query(prompt: String): CompletableFuture<String?> {
		val client = HttpClient.newHttpClient()
		val request = HttpRequest.newBuilder()
			.uri(URI.create("http://localhost:11434/api/generate"))
			.POST(HttpRequest.BodyPublishers.ofString("{ \"model\": \"mistral\",  \"prompt\": \"$prompt\", \"stream\": false }"))
			.build();
		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(onResponse())
	}

	private fun onResponse(): (response: HttpResponse<String>) -> String? {
		return { response ->
			if (response.statusCode() == 200) {
				val ollamaResponse = Serialization.unmarshal(response.body(), OllamaResponse::class.java)
				ollamaResponse.getYaml().joinToString("\n---\n")
			} else {
				null
			}
		}
	}

	private fun onApply(document: Document, editor: FileEditor, balloon: Balloon, project: Project): MouseListener {
		return object: MouseAdapter() {
			override fun mouseReleased(e: MouseEvent?) {
				val content = document.text
				val document = ReadAction.compute<Document, Exception> {
					FileDocumentManager.getInstance().getDocument(editor.file)
				} ?: return
				balloon.hide()
				WriteCommandAction.runWriteCommandAction(project) {
					document.replaceString(0, document.textLength, content)
					PsiDocumentManager.getInstance(project).commitDocument(document)
				}
			}
		}
	}
	private fun startLoading() {
		ApplicationManager.getApplication().invokeLater {
			this.loadingIcon.icon = AnimatedIcon.Default.INSTANCE
			this.editor.contentComponent.isVisible = false
		}
	}

	private fun stopLoading() {
		ApplicationManager.getApplication().invokeLater {
			this.loadingIcon.icon = null
			this.editor.contentComponent.isVisible = true
		}
	}

	@JsonDeserialize(using = JsonDeserializer.None::class)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	private class OllamaResponse {

		private val yamlRegex = Regex("```yaml([^```]+)```")

		@JsonProperty("response")
		val response: String? = null

		fun getYaml(): List<String> {
			if (response == null) {
				return emptyList()
			}
			return yamlRegex.findAll(response)
				.map { match ->
					val groupIndex = if (match.groupValues.size >= 2) {
						1
					} else {
						0
					}
					match.groupValues[groupIndex]
				}
				.toList()
		}
	}
}