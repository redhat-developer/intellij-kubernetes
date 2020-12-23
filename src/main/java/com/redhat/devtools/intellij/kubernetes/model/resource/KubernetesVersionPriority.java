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
package com.redhat.devtools.intellij.kubernetes.model.resource;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import com.redhat.devtools.intellij.kubernetes.model.resource.KubernetesVersionFactory.Version;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A util class that allows to deal with kubernetes versions.
 */
public class KubernetesVersionPriority {

    private KubernetesVersionPriority() {}

    /**
     * Returns the {@link CustomResourceDefinitionVersion} with the highest priority for the given list of CustomResourceDefinitionVersions.
     *
     * @param versions the versions to pick the version with the highest priority from
     * @return the version with the highest priority
     */
    public static String highestPriority(List<String> versions) {
        List<Version> byPriority = getByPriority(versions);
        if (byPriority.isEmpty()) {
            return null;
        }
        return byPriority.get(0).getFull();
    }

    private static List<Version> getByPriority(List<String> versions) {
        if (versions == null
                || versions.isEmpty()) {
            return Collections.emptyList();
        }
        return versions.stream()
                .map(KubernetesVersionFactory::create)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }
}
