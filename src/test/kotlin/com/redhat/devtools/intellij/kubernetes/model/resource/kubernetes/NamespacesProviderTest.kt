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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.nhaarman.mockitokotlin2.verify
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.junit.Test

class NamespacesProviderTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val client = client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
    private val provider = NamespacesProvider(client)

    @Test
    fun `#getAllResources should retrieve all namespaces`() {
        // given
        // when
        provider.allResources
        // then
        verify(client.namespaces().list()).items
    }
}