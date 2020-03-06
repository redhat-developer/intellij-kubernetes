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
package org.jboss.tools.intellij.kubernetes.model.resource

import com.nhaarman.mockitokotlin2.verify
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.client
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString

class NamespacesProviderTest {

    private val client =
        client(NAMESPACE2.metadata.name, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3))
    private val provider =
        NamespacesProvider(client)

    @Test
    fun `#getAllResources() should retrieve all namespaces`() {
        // given
        // when
        provider.getAllResources()
        // then
        verify(client.namespaces().list()).items
    }
}