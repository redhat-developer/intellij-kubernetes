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

import io.fabric8.kubernetes.api.model.Pod
import org.assertj.core.api.Assertions.assertThat
import org.jboss.tools.intellij.kubernetes.model.mocks.ClientMocks.resource
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

	@Test
	fun `#sameResource should return false if both resources have null uid and different selfLink`() {
		// given
		val red = resource<Pod>("agent smith", uid = null, selfLink = "red pill")
		val blue = resource<Pod>("agent smith", uid = null, selfLink = "blue pill")
		// when
		val same = red.sameResource(blue)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#sameResource should return true if both resources have null uid and same selfLink`() {
		// given
		val trinity = resource<Pod>("trinity", uid = null, selfLink = "red pill")
		val merovingian = resource<Pod>("merovingian", uid = null, selfLink = "red pill")
		// when
		val same = trinity.sameResource(merovingian)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#sameResource should return true if both resources have same uid and null selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = null)
		val morpheus = resource<Pod>("morpheus", uid = "red pill", selfLink = null)
		// when
		val same = nemo.sameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#sameResource should return false if both resources have different uid`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = null)
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = null)
		// when
		val same = nemo.sameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#sameResource should return true if both resources have same uid but different selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "red pill", selfLink = "Zion")
		// when
		val same = nemo.sameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#sameResource should return true if both resources have different uid but same selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = "Nebuchadnezzar")
		// when
		val same = nemo.sameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#sameResource should return false if both resources have different uid and selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = "Zion")
		// when
		val same = nemo.sameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}
}