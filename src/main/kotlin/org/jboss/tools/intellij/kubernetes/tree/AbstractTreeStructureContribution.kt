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
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import java.util.function.Predicate

abstract class AbstractTreeStructureContribution(override val model: IResourceModel): ITreeStructureContribution {

    protected fun getRootElement(): Any? {
        return model.currentContext
    }

    protected fun <R: HasMetadata> getSortedResources(kind: Class<R>, namespaced: ResourcesIn, filter: Predicate<R>? = null): Collection<R> {
        return model.getResources(kind, namespaced, filter)
                .sortedBy { it.metadata.name }
    }

}