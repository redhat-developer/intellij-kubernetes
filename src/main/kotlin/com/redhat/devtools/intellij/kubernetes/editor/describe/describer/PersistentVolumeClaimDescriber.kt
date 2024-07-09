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
package com.redhat.devtools.intellij.kubernetes.editor.describe.describer

import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimTemplate

class PersistentVolumeClaimDescriber private constructor(private val metadata: ObjectMeta?, private val spec: PersistentVolumeClaimSpec):
	Describer {
	constructor(pvc: PersistentVolumeClaim): this(pvc.metadata, pvc.spec)
	constructor(pvcTemplate: PersistentVolumeClaimTemplate): this(pvcTemplate.metadata, pvcTemplate.spec)

	override fun addTo(chapter: Chapter): Chapter {
		return addTo(chapter, true)
	}

	fun addTo(chapter: Chapter, full: Boolean): Chapter {
		if (full) {
			chapter.addIfExists("Name", metadata?.name)
			chapter.addIfExists("Namespace", metadata?.namespace)
		}
		return chapter
	}


}