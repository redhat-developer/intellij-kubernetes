/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.actions

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.kubernetes.editor.ResourceEditor
import javax.swing.Icon

class Action(
    val title: String,
    val keyword: String,
    val icon: Icon,
    val description: String,
    val error: String,
    val message: (e: Exception) -> String,
    val callback: (fe: FileEditor, re: ResourceEditor, project: Project) -> Unit
)