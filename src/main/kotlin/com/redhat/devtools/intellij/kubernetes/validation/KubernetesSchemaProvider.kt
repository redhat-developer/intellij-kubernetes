/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.validation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo

class KubernetesSchemaProvider(
	private val info: KubernetesTypeInfo,
	private val schemaFile: VirtualFile
) : JsonSchemaFileProvider {

	private var project: Project? = null

	private constructor(project: Project, info: KubernetesTypeInfo, file: VirtualFile) : this(info, file) {
		this.project = project
	}

	override fun isAvailable(file: VirtualFile): Boolean {
		return ApplicationManager.getApplication().runReadAction(
			Computable<Boolean> {
				val psiFile = PsiManager.getInstance(project!!).findFile(file)
				if (psiFile == null) {
					false
				} else {
					val fileInfo = KubernetesTypeInfo.extractMeta(psiFile)
					info == fileInfo
				}
			})
	}

	override fun getName(): String {
		return info.toString()
	}

	override fun getSchemaFile(): VirtualFile {
		return schemaFile
	}

	override fun getSchemaType(): SchemaType {
		return SchemaType.schema
	}

	fun withProject(project: Project): KubernetesSchemaProvider {
		return if (this.project === project) {
			this
		} else {
			KubernetesSchemaProvider(project, info, schemaFile)
		}
	}
}