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
package com.redhat.devtools.intellij.kubernetes.model.util

import com.intellij.openapi.util.io.FileUtilRt
import java.nio.file.Files
import java.nio.file.Path

/**
 * Adds an addendum to the name of the given file if a file with the same name already exists.
 * Returns the path as is if the path doesn't exist yet.
 * ex. jedi-sword(2).yml where (2) is the addendum that's added so that the filename is unique.
 *
 * @param path the file whose filename should get a unique addendum
 * @return the file with/or without a unique suffix
 *
 * @see [removeAddendum]
 */
fun addAddendum(path: Path): Path {
    if (!Files.exists(path)) {
        return path
    }
    val name = FileUtilRt.getNameWithoutExtension(path.toString())
    val suffix = FileUtilRt.getExtension(path.toString())
    val parent = path.parent
    var i = 1
    var unused: Path?
    do {
        unused = parent.resolve("$name(${i++}).$suffix")
    } while (unused != null && Files.exists(unused))
    return unused!!
}

/**
 * Returns the filename without the addendum that was added to make the given filename unique.
 * Returns the filename as is if it has no addendum.
 * ex. jedi-sword(2).yml where (2) is the suffix that was added so that the filename is unique.
 *
 * @param filename the filename that should be stripped of a unique suffix
 * @return the filename without the unique suffix if it exists
 *
 * @see [addAddendum]
 */
fun removeAddendum(filename: String): String {
    val suffixStart = filename.indexOf('(')
    if (suffixStart < 0) {
        return filename
    }
    val suffixStop = filename.indexOf(')')
    if (suffixStop < 0) {
        return filename
    }
    return filename.removeRange(suffixStart, suffixStop + 1)
}
