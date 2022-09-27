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
package com.redhat.devtools.intellij.kubernetes.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.redhat.devtools.intellij.kubernetes.editor.util.getDocument
import com.redhat.devtools.intellij.kubernetes.model.util.ResourceException
import com.redhat.devtools.intellij.kubernetes.model.util.createResource
import io.fabric8.kubernetes.api.model.HasMetadata

object EditorResourceFactory {

    /**
     * Returns a [HasMetadata] for a given editor instance.
     *
     * @param editor to retrieve the json/yaml from
     * @return [HasMetadata] for the given editor and clients
     */
    fun create(editor: FileEditor): HasMetadata? {
        return create(getDocument(editor))
    }

    /**
     * Returns a [HasMetadata] for a given [Document] instance.
     *
     * @param document to retrieve the json/yaml from
     * @return [HasMetadata] for the given editor and clients
     */
    fun create(document: Document?): HasMetadata? {
        return if (document?.text == null) {
            null
        } else {
            try {
                return createResource(document.text)
            } catch (e: RuntimeException) {
                throw ResourceException("Invalid kubernetes yaml/json", e.cause ?: e)
            }
        }
    }

}