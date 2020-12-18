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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ResourceUtilsTest {

	@Test
	fun `#trimWithEllipsis of string lt than requested length should return it unchanged`() {
		// given
		val name = "abc"
		// when
		val trimmed = trimWithEllipsis(name,100)
		// then
		assertThat(trimmed).isEqualTo(name)
	}

	@Test
	fun `#trimWithEllipsis to length which cannot hold ellipsis should return trimmed without ellipsis`() {
		// given
		val name = "Smurf"
		// when
		val trimmed = trimWithEllipsis(name,3)
		// then
		assertThat(trimmed).isEqualTo("Smu")
	}

	@Test
	fun `#trimWithEllipsis to lt 6 should return first 3 + ellipsis`() {
		// given
		val name = "Smurfette"
		// when
		val trimmed = trimWithEllipsis(name,6)
		// then
		assertThat(trimmed).isEqualTo("Smu...")
	}

	@Test
	fun `#trimWithEllipsis to length that can not 3 starting chars should trim with 2 starting chars, ellipsis and 3 trailing chars`() {
		// given
		val name = "Smurfette"
		// when
		val trimmed = trimWithEllipsis(name,7)
		// then
		assertThat(trimmed).isEqualTo("S...tte")
	}

	@Test
	fun `#trimWithEllipsis to length that can hold 3 starting chars should trim with fitting start, ellipsis and 3 trailing chars`() {
		// given
		val name = "Papa Smurf and Smurfette"
		// when
		val trimmed = trimWithEllipsis(name,10)
		// then
		assertThat(trimmed).isEqualTo("Papa...tte")
	}

}