/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.actions

import com.redhat.devtools.intellij.common.actions.StructureTreeAction
import io.fabric8.kubernetes.api.model.HasMetadata

abstract class UseResourceAction<N: HasMetadata>(filter: Class<N>) : StructureTreeAction(false, filter) {

	override fun isVisible(selected: Any?): Boolean {
		val element = selected?.getElement<HasMetadata>() ?: return false
		val isCurrent = getResourceModel()?.isCurrentNamespace(element) ?: return false
		return !isCurrent
	}

	override fun isVisible(selected: Array<out Any>?): Boolean {
		return super.isVisible(selected)
				&& isVisible(selected?.get(0))
	}
}
