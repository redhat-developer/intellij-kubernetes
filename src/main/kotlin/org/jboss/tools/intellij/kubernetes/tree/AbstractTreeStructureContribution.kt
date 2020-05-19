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
package org.jboss.tools.intellij.kubernetes.tree

import io.fabric8.kubernetes.api.model.HasMetadata
import org.jboss.tools.intellij.kubernetes.model.IResourceModel

abstract class AbstractTreeStructureContribution(override val model: IResourceModel): ITreeStructureContribution {

    protected fun getRootElement(): Any? {
        return model.currentContext
    }

    protected fun getResources(kind: Class<out HasMetadata>?): List<Any> {
        if (kind == null) {
            return mutableListOf()
        }
        return model
            .getResources(kind)
            .sortedBy { it.metadata.name }
            .toMutableList()
    }

}