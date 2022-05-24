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
package com.redhat.devtools.intellij.kubernetes.model.resource

import com.redhat.devtools.intellij.kubernetes.model.util.getApiVersion
import com.redhat.devtools.intellij.kubernetes.model.util.getHighestPriorityVersion
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext

data class ResourceKind<R : HasMetadata> private constructor(
		val version: String,
		val clazz: Class<R>,
		val kind: String
) {

	companion object {
		@JvmStatic
		fun <R: HasMetadata> create(clazz: Class<R>): ResourceKind<R> {
			return ResourceKind(
					removeK8sio(getApiVersion(clazz)),
					clazz,
					clazz.simpleName)
		}

		@JvmStatic
		fun create(resource: HasMetadata): ResourceKind<out HasMetadata> {
			return ResourceKind(
					removeK8sio(resource.apiVersion),
					resource.javaClass,
					resource.kind)
		}

		@JvmStatic
		fun create(spec: CustomResourceDefinitionSpec): ResourceKind<GenericKubernetesResource>? {
			val version = getHighestPriorityVersion(spec) ?: return null
			return ResourceKind(
				removeK8sio(getApiVersion(spec.group, version)),
				GenericKubernetesResource::class.java,
				spec.names.kind)
		}

		@JvmStatic
		fun create(context: CustomResourceDefinitionContext): ResourceKind<GenericKubernetesResource>? {
			return ResourceKind(
				removeK8sio(getApiVersion(context.group, context.version)),
				GenericKubernetesResource::class.java,
				context.kind)
		}

		@JvmStatic
		fun create(version: String, clazz: Class<out HasMetadata>, kind: String): ResourceKind<out HasMetadata> {
			return ResourceKind(version, clazz, kind)
		}

		/**
		 * Removes ".k8s.io" from api groups like "apiextension.k8s.io".
		 * This allows interoperability btw. yaml defined definitions that frequently use "apiextension.k8s.io"
		 * while annotation in kubernetes-client use "apiextensions".
		 *
		 * <ul>
		 *		<li>
		 * 			apiVerison defined in the annotation for CustomResourceDefinition
		 * 			<pre>private String apiVersion = "apiextensions/v1beta1";</pre>
		 * 		</li>
		 * 		<li>
		 * 			apiVersion defined in yaml
		 * 			<pre>apiextensions.k8s.io/v1beta1</pre>
		 * 		</li>
		 * </ul>
		 *
		 * @see CustomResourceDefinition.apiVersion
		 */
		@JvmStatic
		private fun removeK8sio(group: String): String {
			return group.replaceFirst("apiextensions.k8s.io", "apiextensions")
		}
	}
}