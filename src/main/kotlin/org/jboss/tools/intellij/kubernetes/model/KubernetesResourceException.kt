package org.jboss.tools.intellij.kubernetes.model

class KubernetesResourceException(message: String, exception: Exception): RuntimeException(message, exception) {
}