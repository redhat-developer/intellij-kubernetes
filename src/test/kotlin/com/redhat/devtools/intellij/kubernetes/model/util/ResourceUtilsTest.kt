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
import io.fabric8.openshift.api.model.BuildConfig
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
	fun `#isSameResource should return true if both resources only differ in version`() {
		// given
		val version1001 = resource<Pod>("nemo", "ns", "red pill", "v1","1001")
		val version2002 = resource<Pod>("nemo", "ns", "red pill", "v1","2002")
		// when
		val same = version1001.isSameResource(version2002)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return true if both resources differ in uid`() {
		// given
		val redPillUid = resource<Pod>("nemo", "ns", "red pill", "v1","1")
		val bluePillUid = resource<Pod>("nemo", "ns", "blue pill", "v1","1")
		// when
		val same = redPillUid.isSameResource(bluePillUid)
		// then difference in uid should not matter
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in name`() {
		// given
		val nemo = resource<Pod>("nemo", "ns", "uid", "v1","1")
		val morpheus = resource<Pod>("morpheus", "ns", "uid", "v1","1")
		// when
		val same = nemo.isSameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in namespace`() {
		// given
		val matrixNamespace = resource<Pod>("nemo", "matrix", "uid", "v1","1")
		val zionNamespace = resource<Pod>("nemo", "zion", "uid", "v1","1")
		// when
		val same = matrixNamespace.isSameResource(zionNamespace)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources have different kind`() {
		// given
		val pod = resource<Pod>("nemo", "zion", "uid", "v1","link")
		val buildConfig = resource<BuildConfig>("nemo", "zion", "uid", "v1","link")
		// when
		val same = pod.isSameResource(buildConfig)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in apiVersion`() {
		// given
		val apiVersion1 = resource<Pod>("nemo", "matrix", "uid", "v1","1")
		val apiVersion2 = resource<Pod>("nemo", "zion", "uid", "v2","1")
		// when
		val same = apiVersion1.isSameResource(apiVersion2)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if both resources have same version`() {
		// given
		val sameVersion1 = resource<Pod>("neo", "ns","uid", "v1", "1")
		val sameVersion2 = resource<Pod>("neo", "ns","uid", "v1", "1")
		// when
		val newer = sameVersion1.isNewerVersionThan(sameVersion2)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return true if this resource has older version than given`() {
		// given
		val older = resource<Pod>("neo", "ns","uid", "v1", "1")
		val newer = resource<Pod>("neo", "ns","uid", "v1", "2")
		// when
		val isNewer = newer.isNewerVersionThan(older)
		// then
		assertThat(isNewer).isTrue()
	}

	@Test
	fun `#isNewerVersionThan should return true if given resource has newer version`() {
		// given
		val older = resource<Pod>("neo", "ns","uid", "v1", "1")
		val newer = resource<Pod>("neo", "ns", "uid", "v1", "2")
		// when
		val isNewer = older.isNewerVersionThan(newer)
		// then
		assertThat(isNewer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if this resource has no version, given resource has a version`() {
		// given
		val noVersion = resource<Pod>("neo", "ns","uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "ns","uid", "v1","1")
		// when
		val newer = noVersion.isNewerVersionThan(hasVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return true if this resource has version, given resource has no version`() {
		// given
		val noVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val newer = hasVersion.isNewerVersionThan(noVersion)
		// then
		assertThat(newer).isTrue()
	}

	@Test
	fun `#isNewerVersionThan should return false if resource do NOT have same name`() {
		// given
		val neo = resource<Pod>("neo", "ns", "uid", "v1","2")
		val morpheus = resource<Pod>("morpheus", "ns", "uid", "v1","1")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if resource have different same namespace`() {
		// given
		val neo = resource<Pod>("neo", "matrix", "uid", "v1","2")
		val morpheus = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}

}