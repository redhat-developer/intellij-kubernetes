package org.jboss.tools.intellij.kubernetes.model.util

import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.Config
import io.fabric8.kubernetes.api.model.NamedCluster
import io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY
import io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import io.fabric8.kubernetes.client.utils.IOHelpers
import io.fabric8.kubernetes.client.utils.Utils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Locale

class KubeConfigClusters {

	val clusters: List<NamedCluster>
		get() {
			return config?.clusters ?: emptyList()
		}

	private val current: NamedCluster?
		get() {
			val context = KubeConfigUtils.getCurrentContext(config)
			return clusters.find { it.name == context.cluster }
		}

	fun isCurrent(cluster: NamedCluster): Boolean {
		return cluster == current
	}

	private val config: Config?
		get() {
			if (!Utils.getSystemPropertyOrEnvVar(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, true)) {
				return null
			}
			val kubeConfigFile = getKubeConfigFile() ?: return null
			logger<KubeConfigClusters>().debug("Found for Kubernetes config at: [${kubeConfigFile.path}].")
			val contents = getKubeConfigContents(kubeConfigFile)
			return KubeConfigUtils.parseConfigFromString(contents)
		}

	private fun getKubeConfigContents(file: File): String? {
		var kubeconfigContents: String? = null
		try {
			FileReader(file).use { reader -> kubeconfigContents = IOHelpers.readFully(reader) }
		} catch (e: IOException) {
			logger<KubeConfigClusters>().error("Could not load Kubernetes config file from ${file.path}", e)
		}
		return kubeconfigContents
	}

	private fun getKubeConfigFile(): File? {
		val fileName = getKubeConfigFilename() ?: return null
		val kubeConfigFile = File(fileName)
		if (!kubeConfigFile.isFile) {
			return null
		}
		return kubeConfigFile
	}

	private fun getKubeConfigFilename(): String? {
		val fileName = Utils.getSystemPropertyOrEnvVar(KUBERNETES_KUBECONFIG_FILE,
				File(getHomeDir(),".kube" + File.separator + "config").toString());

		// if system property/env var contains multiple files take the first one based on the environment
		// we are running in (eg. : for Linux, ; for Windows)
		val fileNames: List<String> = fileName.split(File.pathSeparator);

		if (fileNames.isEmpty()) {
			return null
		}
		logger<KubeConfigClusters>().warn(
				"Found multiple Kubernetes config files [$fileNames], using the first one: [$fileNames[0]]. " +
						"If not desired file, please change it by doing `export KUBECONFIG=/path/to/kubeconfig` " +
						"on Unix systems or `\$Env:KUBECONFIG=/path/to/kubeconfig` on Windows.");
		return fileNames[0];
	}

	private	fun getHomeDir(): String? {
			val osName = System.getProperty("os.name").toLowerCase(Locale.ROOT)
			if (osName.startsWith("win")) {
				val homeDrive = System.getenv("HOMEDRIVE")
				val homePath = System.getenv("HOMEPATH")
				if (homeDrive != null && homeDrive.isNotEmpty() && homePath != null && homePath.isNotEmpty()) {
					val homeDir = homeDrive + homePath
					val f = File(homeDir)
					if (f.exists() && f.isDirectory) {
						return homeDir
					}
				}
				val userProfile = System.getenv("USERPROFILE")
				if (userProfile != null && userProfile.isNotEmpty()) {
					val f = File(userProfile)
					if (f.exists() && f.isDirectory) {
						return userProfile
					}
				}
			}
			val home = System.getenv("HOME")
			if (home != null && home.isNotEmpty()) {
				val f = File(home)
				if (f.exists() && f.isDirectory) {
					return home
				}
			}

			//Fall back to user.home should never really get here
			return System.getProperty("user.home", ".")
		}
}