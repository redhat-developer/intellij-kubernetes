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
package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.*
import java.util.function.Predicate

val resourceName: (HasMetadata) -> String? = { it.metadata.name }

class Listable<R: HasMetadata>(
		private val resourceKind: Class<R>,
		private val resourceIn: ResourcesIn,
		private val filter: Predicate<R>? = null,
		private val model: ResourceModel) {

		fun list(): Collection<R> {
			return model.getResources(resourceKind, resourceIn, filter)
		}
}

class Filterable<R: HasMetadata>(
		private val resourceKind: Class<R>,
		private val resourceIn: ResourcesIn,
		private val model: ResourceModel) {

	fun list(): Collection<R> {
		return model.getResources(resourceKind, resourceIn)
	}

	fun filtered(filter: Predicate<R>): Listable<R> {
		return Listable(resourceKind, resourceIn, filter, model)
	}
}

class Namespaceable<R: HasMetadata>(
		private val resourceKind: Class<R>,
		private val model: ResourceModel) {

	fun inAnyNamespace(): Filterable<R> {
		return Filterable(resourceKind, ResourcesIn.ANY_NAMESPACE, model)
	}

	fun inCurrentNamespace(): Filterable<R> {
		return Filterable(resourceKind, ResourcesIn.CURRENT_NAMESPACE, model)
	}

	fun inNoNamespace(): Filterable<R> {
		return Filterable(resourceKind, ResourcesIn.NO_NAMESPACE, model)
	}
}

fun <R: HasMetadata> Collection<R>.sortByResourceName(): List<R> {
	return this.sortedBy { it.metadata.name }
}
