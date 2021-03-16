/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.icons.AllIcons
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Test that assert that icons that are referenced in plugin.xml can be loaded.
 */
class PluginXmlIconsTest {

	@Test
	fun `AllIcons#Actions#Close exists`() {
		// when
		val icon = AllIcons.Actions.Close
		// then
		assertThat(icon).isNotNull
	}

	@Test
	fun `AllIcons#Actions#Refresh exists`() {
		// when
		val icon = AllIcons.Actions.Refresh
		// then
		assertThat(icon).isNotNull
	}

	@Test
	fun `AllIcons#Nodes#EmptyNode exists`() {
		// given
		// when
		val icon = AllIcons.Nodes.EmptyNode
		// then
		assertThat(icon).isNotNull
	}

}