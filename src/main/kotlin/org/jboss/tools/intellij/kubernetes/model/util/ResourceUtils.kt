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
package org.jboss.tools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil

/**
 * returns {@code true} if the given resources are equal.
 * These are considered equal if their name, namespace and kind are equal.
 */
fun areEqual(thisResource: HasMetadata, thatResource: HasMetadata): Boolean {
	return KubernetesResourceUtil.getName(thisResource) == KubernetesResourceUtil.getName(thatResource)
			&& KubernetesResourceUtil.getNamespace(thisResource) == KubernetesResourceUtil.getNamespace(thatResource)
			&& KubernetesResourceUtil.getKind(thisResource) == KubernetesResourceUtil.getKind(thatResource)
}
