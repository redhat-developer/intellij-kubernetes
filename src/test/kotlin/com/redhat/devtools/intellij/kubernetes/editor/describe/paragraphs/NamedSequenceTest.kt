/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NamedSequenceTest {

	@Test
	fun `#addIfExists adds if value is NOT null`() {
		// given
		val sequence = NamedSequence("jedis")
		assertThat(sequence.children).isEmpty()
		// when
		sequence.addIfExists("obiwan")
		// then
		assertThat(sequence.children).hasSize(1)
	}

	@Test
	fun `#addIfExists does NOT add if value is null`() {
		// given
		val sequence = NamedSequence("jedis")
		assertThat(sequence.children).isEmpty()
		// when
		sequence.addIfExists(null)
		// then
		assertThat(sequence.children).isEmpty()
	}

	@Test
	fun `#addIfExists does NOT add if value is blank string`() {
		// given
		val sequence = NamedSequence("jedis")
		assertThat(sequence.children).isEmpty()
		// when
		sequence.addIfExists(" ")
		// then
		assertThat(sequence.children).isEmpty()
	}
}
