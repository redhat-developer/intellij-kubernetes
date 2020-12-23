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

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Pod
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceKindTest {

    @Test
    fun `#ResourceKinds for same Class are equal`() {
        // given
        val kind1 = ResourceKind.create(Pod::class.java)
        val kind2 = ResourceKind.create(Pod::class.java)
        // when
        // then
        assertThat(kind1).isEqualTo(kind2)
    }

    @Test
    fun `#ResourceKinds for different Classes are not equal`() {
        // given
        val podKind = ResourceKind.create(Pod::class.java)
        val namespaceKind = ResourceKind.create(Namespace::class.java)
        // when
        // then
        assertThat(podKind).isNotEqualTo(namespaceKind)
    }

    @Test
    fun `#ResourceKinds for class and instance are equal`() {
        // given
        val classKind = ResourceKind.create(Pod::class.java)
        val instanceKind = ResourceKind.create(Pod())
        // when
        // then
        assertThat(classKind).isEqualTo(instanceKind)
    }

    @Test
    fun `#ResourceKinds for with apiVersion 'apiextensions' and 'apiextensions-k8s-io' are equal`() {
        // given
        // version as defined in annotations to CustomResourceDefinition.class
        // private String apiVersion = "apiextensions/v1beta1";
        val apiextensionKind = ResourceKind.create("apiextensions/v1beta1", HasMetadata::class.java, "CustomResourceDefinition")
        // version as defined in yaml
        // apiVersion: apiextensions.k8s.io/v1beta1
        val apiextensionK8sioKind = ResourceKind.create("apiextensions.k8s.io/v1beta1", HasMetadata::class.java, "CustomResourceDefinition")
        // when
        // then
        assertThat(apiextensionKind).isNotEqualTo(apiextensionK8sioKind)
        assertThat(apiextensionK8sioKind).isNotEqualTo(apiextensionKind)
    }

}