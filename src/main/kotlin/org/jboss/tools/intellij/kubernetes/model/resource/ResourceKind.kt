package org.jboss.tools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.model.annotation.ApiGroup
import io.fabric8.kubernetes.model.annotation.ApiVersion
import io.fabric8.kubernetes.model.util.Helper
import org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.GenericCustomResource

data class ResourceKind<R : HasMetadata> private constructor(
		val version: String,
		val clazz: Class<R>,
		val kind: String = clazz.simpleName
) {

	companion object {
		fun <R: HasMetadata> new(clazz: Class<R>): ResourceKind<R> {
			val version = getVersion(clazz)
			return ResourceKind(version, clazz)
		}

		@JvmStatic
		fun new(definition: CustomResourceDefinition): ResourceKind<GenericCustomResource> {
			return ResourceKind(
					getVersion(definition.spec.group, definition.spec.version),
					GenericCustomResource::class.java,
					definition.metadata.name)
		}

		@JvmStatic
		fun <R: HasMetadata> new(apiGroup: String, apiVersion: String, clazz: Class<R>): ResourceKind<R> {
			val version = getVersion(apiGroup, apiVersion)
			return ResourceKind(version, clazz)
		}

		private fun getVersion(clazz: Class<out KubernetesResource>): String {
			val apiGroup = Helper.getAnnotationValue(clazz, ApiGroup::class.java)
			val apiVersion = Helper.getAnnotationValue(clazz, ApiVersion::class.java)
			return if (apiGroup != null && apiGroup.isNotBlank()
					&& apiVersion != null && apiVersion.isNotBlank()) {
				getVersion(apiGroup, apiVersion)
			} else {
				clazz.simpleName
			}
		}

		private fun getVersion(apiGroup: String, apiVersion: String) = "$apiGroup/$apiVersion"

	}
}