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
package com.redhat.devtools.intellij.kubernetes.editor.mocks

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLValue
import kotlin.random.Random

fun createYAMLKeyValue(
	key: String = Random.nextInt().toString(),
	value: String? = null,
	parent: YAMLKeyValue? = null,
	project: Project = mock()
): YAMLKeyValue {
	val valueElement: YAMLValue = mock {
		on { getText() } doReturn value
	}
	return mock {
		on { getName() } doReturn key
		on { getKeyText() } doReturn key
		on { getValue() } doReturn valueElement
		on { getParent() } doReturn parent
		on { getProject() } doReturn project
	}
}

fun createJsonProperty(
	name: String = Random.nextInt().toString(),
	value: String? = null,
	parent: JsonProperty? = null,
	project: Project
): JsonProperty {
	val valueElement: JsonValue = mock {
		on { getText() } doReturn value
	}
	return mock {
		on { getValue() } doReturn valueElement
		on { getName() } doReturn name
		on { getValue() } doReturn valueElement
		on { getParent() } doReturn parent
		on { getProject() } doReturn project
	}
}

fun createProjectWithServices(
	yamlGenerator: YAMLElementGenerator? = null,
	psiFileFactory: PsiFileFactory? = null
): Project {
	return mock<Project> {
		on { getService(any<Class<*>>()) } doAnswer { invocation ->
			when {
				YAMLElementGenerator::class.java == invocation.getArgument<Class<*>>(0) ->
					yamlGenerator

				PsiFileFactory::class.java == invocation.getArgument<Class<*>>(0) ->
					psiFileFactory

				else -> null
			}
		}
	}
}

fun createYAMLFile(documents: List<YAMLDocument>?): YAMLFile {
	return mock<YAMLFile> {
		on { getDocuments() } doReturn documents
	}
}

fun createYAMLValue(children: Array<YAMLPsiElement>): YAMLValue {
	return mock {
		on { getChildren() } doReturn children
	}
}

fun createYAMLDocument(yamlValue: YAMLValue): YAMLDocument {
	return mock {
		on { getTopLevelValue() } doReturn yamlValue
	}
}


fun createJsonPsiFile(properties: List<JsonProperty>): PsiFile {
	val firstChild: JsonObject = mock {
		on { getPropertyList() } doReturn properties
	}
	return mock {
		on { getFirstChild() } doReturn firstChild
	}
}

fun createJsonPsiFileFactory(properties: List<JsonProperty>): PsiFileFactory {
	val file = createJsonPsiFile(properties)
	return createPsiFileFactory(file)
}

fun createPsiFileFactory(psiFile: PsiFile): PsiFileFactory {
	return mock<PsiFileFactory> {
		on { createFileFromText(any<String>(), any<FileType>(), any<String>()) } doReturn psiFile
	}
}

fun createYAMLGenerator(): YAMLElementGenerator {
	return mock<YAMLElementGenerator> {
		on { createYamlKeyValue(any<String>(), any<String>()) } doReturn mock()
	}
}
