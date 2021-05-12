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
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.api.model.Pod
import org.assertj.core.api.Assertions.assertThat
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
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
	fun `#isSameResource should return false if both resources have null uid and different selfLink`() {
		// given
		val red = resource<Pod>("agent smith", uid = null, selfLink = "red pill")
		val blue = resource<Pod>("agent smith", uid = null, selfLink = "blue pill")
		// when
		val same = red.isSameResource(blue)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return true if both resources have null uid and same selfLink`() {
		// given
		val trinity = resource<Pod>("trinity", uid = null, selfLink = "red pill")
		val merovingian = resource<Pod>("merovingian", uid = null, selfLink = "red pill")
		// when
		val same = trinity.isSameResource(merovingian)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return true if both resources have same uid and null selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = null)
		val morpheus = resource<Pod>("morpheus", uid = "red pill", selfLink = null)
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return false if both resources have different uid`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = null)
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = null)
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return true if both resources have same uid but different selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "red pill", selfLink = "Zion")
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return true if both resources have different uid but same selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = "Nebuchadnezzar")
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return false if both resources have different uid and selfLink`() {
		// given
		val nemo = resource<Pod>("nemo", uid = "red pill", selfLink = "Nebuchadnezzar")
		val morpheus = resource<Pod>("morpheus", uid = "blue pill", selfLink = "Zion")
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if both resources have same version`() {
		// given
		val neo1 = resource<Pod>("neo", resourceVersion = "1")
		val neo2 = resource<Pod>("neo", resourceVersion = "1")
		// when
		val newer = neo1.isNewerVersionThan(neo2)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if resource has older version`() {
		// given
		val older = resource<Pod>("neo", resourceVersion = "1")
		val newer = resource<Pod>("neo", resourceVersion = "2")
		// when
		val isNewer = newer.isNewerVersionThan(older)
		// then
		assertThat(isNewer).isTrue()
	}

	@Test
	fun `#isNewerVersionThan should return true if resources has newer version`() {
		// given
		val older = resource<Pod>("neo", resourceVersion = "1")
		val newer = resource<Pod>("neo", resourceVersion = "2")
		// when
		val isNewer = older.isNewerVersionThan(newer)
		// then
		assertThat(isNewer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return true if this resource has no version`() {
		// given
		val noVersion = resource<Pod>("neo", resourceVersion = null)
		val hasVersion = resource<Pod>("neo", resourceVersion = "1")
		// when
		val newer = noVersion.isNewerVersionThan(hasVersion)
		// then
		assertThat(newer).isTrue()
	}

	@Test
	fun `#isNewerVersionThan should return false if given resource has no version`() {
		// given
		val noVersion = resource<Pod>("neo", resourceVersion = null)
		val hasVersion = resource<Pod>("neo", resourceVersion = "1")
		// when
		val newer = hasVersion.isNewerVersionThan(noVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if given resource has same version`() {
		// given
		val sameVersion1 = resource<Pod>("neo", resourceVersion = "1")
		val sameVersion2 = resource<Pod>("neo", resourceVersion = "1")
		// when
		val newer = sameVersion1.isNewerVersionThan(sameVersion2)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return true if resource do NOT have same name`() {
		// given
		val neo = resource<Pod>("neo", resourceVersion = "2")
		val morpheus = resource<Pod>("morpheus", resourceVersion = "1")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}
}