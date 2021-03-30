package com.redhat.devtools.intellij.kubernetes.validation

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.net.URI
import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class KubernetesSchemasProviderFactory: JsonSchemaProviderFactory {

	companion object {
		private val LOGGER = LoggerFactory.getLogger(KubernetesSchemasProviderFactory::class.java)
		private val BASE_DIR = "/schemas/k8s.io"
		private val ALL_JSON = "all.json"
		private val DEFINITIONS_JSON = "_definitions.json"
		private val SUFFIX_JSON = ".json"
	}

	private val providers: List<KubernetesSchemaProvider> = listOf()
		get() {
			return if (field.isEmpty()) {
				load()
			} else {
				field
			}
		}

	override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
		return providers.map { it.withProject(project) }
	}

	private fun load(): List<KubernetesSchemaProvider> {
		val url: URL = javaClass.getResource("$BASE_DIR")
		val uri = url.toURI()

		return getFileSystem(uri).use { fileSystem ->
			if (fileSystem == null) {
				return emptyList()
			}
			return Files.walk(fileSystem.getPath(BASE_DIR))
				.filter { path ->
					!path.fileName.endsWith(DEFINITIONS_JSON)
							|| !path.fileName.endsWith(ALL_JSON)
							|| !path.fileName.endsWith(SUFFIX_JSON)
				}
				.map(::createProvider)
				.filter { it != null }
				.collect(Collectors.toList()) as List<KubernetesSchemaProvider>
		}
	}

	private fun getFileSystem(uri: URI): FileSystem? {
		val uriPortions = uri.toString().split("!")
		return try {
			FileSystems.getFileSystem(URI.create(uriPortions[0]))
		} catch(e: FileSystemNotFoundException) {
			FileSystems.newFileSystem(URI.create(uriPortions[0]), mapOf<String, String>())
		}
	}

	private fun createProvider(path: Path): KubernetesSchemaProvider? {
		val type = createKubernetesTypeInfo(path)
		val file = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.convertFromUrl(path.toUri().toURL()))
		return KubernetesSchemaProvider(type!!, file!!)
	}

	private fun createKubernetesTypeInfo(path: Path): KubernetesTypeInfo? {
		val file = javaClass.getResourceAsStream(path.toString())
		val content: Map<String, Any> = ObjectMapper().readValue(DataInputStream(file), object : TypeReference<Map<String, Any>>() {})
		if (content.isEmpty()) {
			return null
		}
		val groupVersionKind = content["x-kubernetes-group-version-kind"] as? Map<String, Any>?: return null
		val group = groupVersionKind["group"] as String
		val kind = groupVersionKind["kind"] as String
		val version = groupVersionKind["version"] as String
		return KubernetesTypeInfo("$group/$version", kind)
	}

	private fun createProvider(schema: String): KubernetesSchemaProvider? {
		val url = KubernetesSchemasProviderFactory::class.java.getResource("/schemas/$schema") ?: return null
		val info = KubernetesTypeInfo.fromFileName(schema)
		val file = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.convertFromUrl(url))
		return KubernetesSchemaProvider(info!!, file!!)
	}
}