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
package org.jboss.tools.intellij.kubernetes.model.resource;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesVersionPriorityTest {

    @Test
    public void should_version_with_highest_priority() {
        // given
        String highest = "v10";
        List<String> versions = Arrays.asList(
                "foo10",
                "v11alpha2",
                "foo1",
                "v3beta1",
                "v2",
                "v10beta3",
                highest,
                "v11beta2",
                "v1",
                "v12alpha1"
        );
        // when
        String computed = KubernetesVersionPriority.highestPriority(versions);
        // then
        assertThat(computed).isEqualTo(highest);
    }
}
