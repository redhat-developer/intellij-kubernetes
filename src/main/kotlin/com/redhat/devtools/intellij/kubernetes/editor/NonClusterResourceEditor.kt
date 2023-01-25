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
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import io.fabric8.kubernetes.api.model.HasMetadata

class NonClusterResourceEditor(
    editor: FileEditor,
    resourceModel: IResourceModel,
    project: Project,
    notify: (re: ResourceEditor, project: Project, deleted: Boolean, resource: HasMetadata, modified: Boolean, clusterResource: ClusterResource?) -> Unit
) : ResourceEditor(
    editor,
    resourceModel,
    project,
    notify = notify
) {
    override val clusterResource: ClusterResource? get() = null
}