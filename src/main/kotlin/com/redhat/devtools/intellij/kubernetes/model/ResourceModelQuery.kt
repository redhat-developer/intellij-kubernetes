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
package com.redhat.devtools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.context.IActiveContext.ResourcesIn
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import java.util.function.Predicate

/**
 * Interfaces and implementations that allow fluent queries of the resource model
 *
 * @see IResourceModel.resources(ResourceKind)
 * @see IResourceModel.resources(CustomResourceDefinition)
 */

val resourceName: (HasMetadata) -> String? = { it.metadata.name }

interface IListable<R : HasMetadata> {
	fun list(): Collection<R>
}

class ListableResources<R : HasMetadata>(
		private val resourceKind: ResourceKind<R>,
		private val resourceIn: ResourcesIn,
		private val filter: Predicate<R>? = null,
		private val model: ResourceModel) : IListable<R> {

	override fun list(): Collection<R> {
		return model.getAllResources(resourceKind, resourceIn, filter)
	}
}

class ListableCustomResources(
		private val definition: CustomResourceDefinition,
		private val model: ResourceModel) : IListable<HasMetadata> {

	override fun list(): Collection<HasMetadata> {
		return model.getAllResources(definition)
	}
}

interface IFilterable<R : HasMetadata> {
	fun list(): Collection<R>
	fun filtered(filter: Predicate<R>): IListable<R>
}

class FilterableResources<R : HasMetadata>(
		private val resourceKind: ResourceKind<R>,
		private val resourceIn: ResourcesIn,
		private val model: ResourceModel) : IFilterable<R> {

	override fun list(): Collection<R> {
		return model.getAllResources(resourceKind, resourceIn)
	}

	override fun filtered(filter: Predicate<R>): IListable<R> {
		return ListableResources(resourceKind, resourceIn, filter, model)
	}
}

class Namespaceable<R : HasMetadata>(
		private val resourceKind: ResourceKind<R>,
		private val model: ResourceModel) {

	fun inAnyNamespace(): FilterableResources<R> {
		return FilterableResources(resourceKind, ResourcesIn.ANY_NAMESPACE, model)
	}

	fun inCurrentNamespace(): FilterableResources<R> {
		return FilterableResources(resourceKind, ResourcesIn.CURRENT_NAMESPACE, model)
	}

	fun inNoNamespace(): FilterableResources<R> {
		return FilterableResources(resourceKind, ResourcesIn.NO_NAMESPACE, model)
	}
}
