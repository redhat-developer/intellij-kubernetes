package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata

interface ResourceKindProvider {
    open val kind: Class<out HasMetadata>
    open val resources: List<out HasMetadata>
}