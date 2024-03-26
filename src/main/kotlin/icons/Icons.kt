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
package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object Icons {

    @JvmField
    val consolesToolwindow = loadIcon("icons/consoles-toolwindow.svg")
    @JvmField
    val download = loadIcon("icons/download.svg")
    @JvmField
    val upload = loadIcon("icons/upload.svg")
    @JvmField
    val uploadModified = loadIcon("icons/upload-modified.svg")
    @JvmField
    val diff = loadIcon("icons/diff.svg")
    @JvmField
    val removeClutter = loadIcon("icons/remove-clutter.svg")
    @JvmField
    val consoles = loadIcon("icons/consoles.svg")
    @JvmField
    val terminal = loadIcon("icons/terminal.svg")
    @JvmField
    val ai = loadIcon("icons/ai.svg")

    private fun loadIcon(path: String): Icon {
        return IconLoader.getIcon(path, Icons::class.java)
    }
}