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

import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinition
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.customResourceDefinitionVersion
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom.CustomResourceScope
import com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.hasmetadata.HasMetadataResource
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.openshift.api.model.BuildConfig
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

	@Test
	fun `#isSameName should return false if both resources differ in name`() {
		// given
		val morpheus = resource<Pod>("morpheus", "zion", "uid", "v1","1")
		val neo = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val same = morpheus.isSameName(neo)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameName should return true if both resources have same name`() {
		// given
		val neo1 = resource<Pod>("neo", "zion1", "uid1", "v1","1")
		val neo2 = resource<Pod>("neo", "zion2", "uid2", "v2","2")
		// when
		val same = neo1.isSameName(neo2)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameNamespace should return false if both resources differ in namespace`() {
		// given
		val neoMatrix = resource<Pod>("neo", "matrix", "uid", "v1","1")
		val neoZion = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val same = neoMatrix.isSameNamespace(neoZion)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameNamespace should return true if both resources have same namespace`() {
		// given
		val neoMatrix = resource<Pod>("neo1", "zion", "uid1", "v1","1")
		val neoZion = resource<Pod>("neo2", "zion", "uid2", "v2","2")
		// when
		val same = neoMatrix.isSameNamespace(neoZion)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameKind should return false if both resources differ in kind`() {
		// given
		val neoMatrix = resource<Service>("neo1", "zion1", "uid", "v1","1")
		val neoZion = resource<Pod>("neo2", "zion2", "uid", "v1","1")
		// when
		val same = neoMatrix.isSameKind(neoZion)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameKind should return true if both resources have same kind`() {
		// given
		val neoMatrix = resource<Pod>("neo", "matrix", "uid1", "v2","1")
		val neoZion = resource<Pod>("neo", "zion", "uid2", "v2","2")
		// when
		val same = neoMatrix.isSameKind(neoZion)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return true if both resources only differ in version`() {
		// given
		val version1001 = resource<Pod>("neo", "ns", "red pill", "v1","1001")
		val version2002 = resource<Pod>("neo", "ns", "red pill", "v1","2002")
		// when
		val same = version1001.isSameResource(version2002)
		// then
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return true if both resources differ in uid`() {
		// given
		val redPillUid = resource<Pod>("neo", "ns", "red pill", "v1","1")
		val bluePillUid = resource<Pod>("neo", "ns", "blue pill", "v1","1")
		// when
		val same = redPillUid.isSameResource(bluePillUid)
		// then difference in uid should not matter
		assertThat(same).isTrue()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in name`() {
		// given
		val neo = resource<Pod>("neo", "ns", "uid", "v1","1")
		val morpheus = resource<Pod>("morpheus", "ns", "uid", "v1","1")
		// when
		val same = neo.isSameResource(morpheus)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in namespace`() {
		// given
		val matrixNamespace = resource<Pod>("neo", "matrix", "uid", "v1","1")
		val zionNamespace = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val same = matrixNamespace.isSameResource(zionNamespace)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources have different kind`() {
		// given
		val pod = resource<Pod>("neo", "zion", "uid", "v1","link")
		val buildConfig = resource<BuildConfig>("neo", "zion", "uid", "v1","link")
		// when
		val same = pod.isSameResource(buildConfig)
		// then
		assertThat(same).isFalse()
	}

	@Test
	fun `#isSameResource should return false if both resources differ in apiVersion`() {
		// given
		val apiVersion1 = resource<Pod>("neo", "matrix", "uid", "v1","1")
		val apiVersion2 = resource<Pod>("neo", "zion", "uid", "v2","1")
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
	fun `#isNewerVersionThan should return true if this resource has older version`() {
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
	fun `#isNewerVersionThan should return false if one resource has no version, other resource has a version`() {
		// given
		val noVersion = resource<Pod>("neo", "ns","uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "ns","uid", "v1","1")
		// when
		val newer = noVersion.isNewerVersionThan(hasVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return true if one resource has version, other one has no version`() {
		// given
		val noVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val newer = hasVersion.isNewerVersionThan(noVersion)
		// then
		assertThat(newer).isTrue()
	}

	@Test
	fun `#isNewerVersionThan should return false if both resources have no version`() {
		// given
		val noVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		// when
		val newer = hasVersion.isNewerVersionThan(noVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if resources have different name`() {
		// given
		val neo = resource<Pod>("neo", "ns", "uid", "v1","1")
		val morpheus = resource<Pod>("morpheus", "ns", "uid", "v1","2")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if resources have different namespace`() {
		// given
		val neo = resource<Pod>("neo", "matrix", "uid", "v1","1")
		val morpheus = resource<Pod>("neo", "zion", "uid", "v1","2")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isNewerVersionThan should return false if resources have different api version`() {
		// given
		val neo = resource<Pod>("neo", "zion", "uid", "v1","1")
		val morpheus = resource<Pod>("neo", "zion", "uid", "v2","2")
		// when
		val newer = morpheus.isNewerVersionThan(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#getApiVersion should concatenate group and version with delimiter`() {
		// given
		val group = "group"
		val version = "version"
		// when
		val apiVersion = getApiVersion(group, version)
		// then
		assertThat(apiVersion).isEqualTo("$group$API_GROUP_VERSION_DELIMITER$version")
	}

	@Test
	fun `#getApiVersion should concatenate group and version found in class annotation`() {
		// given
		// StorageClass
		// @Version("v1")
		// @Group("storage.k8s.io")
		// when
		val apiVersion = getApiVersion(StorageClass::class.java)
		// then
		assertThat(apiVersion).isEqualTo("storage.k8s.io/v1")
	}

	@Test
	fun `#getApiVersion should return only version if there is no group`() {
		// given
		// Pod
		// @Version("v1")
		// @Group("")
		// when
		val apiVersion = getApiVersion(Pod::class.java)
		// then
		assertThat(apiVersion).isEqualTo("v1")
	}

	@Test
	fun `#getApiVersion should return simple class name if class has annotations`() {
		// given
		// Pod
		// @Version("v1")
		// @Group("")
		// when
		val apiVersion = getApiVersion(HasMetadata::class.java)
		// then
		assertThat(apiVersion).isEqualTo(HasMetadata::class.java.simpleName)
	}

	@Test
	fun `#getApiGroupAndVersion should return group and version if both exist in apiVersion`() {
		// given
		val group = "neoGroup"
		val version = "neoVersion"
		val neo = resource<Pod>("neo", "zion", "uid", "$group/$version","1")
		// when
		val groupAndVersion = getApiGroupAndVersion(neo)
		// then
		assertThat(groupAndVersion.first).isEqualTo(group)
		assertThat(groupAndVersion.second).isEqualTo(version)
	}

	@Test
	fun `#getApiGroupAndVersion should return version if no group exists`() {
		// given
		val version = "neoVersion"
		val neo = resource<Pod>("neo", "zion", "uid", "$version","1")
		// when
		val groupAndVersion = getApiGroupAndVersion(neo)
		// then
		assertThat(groupAndVersion.first).isNull()
		assertThat(groupAndVersion.second).isEqualTo(version)
	}

	@Test
	fun `#isMatchingSpec should return true if crd has spec with same kind, group and version`() {
		// given
		val group = null
		val version = "v1"
		val neo = resource<HasMetadataResource>("neo", "zion", "uid", getApiVersion(group, version), "1")
		val kind = neo.kind!!
		val crd = customResourceDefinition(
			"cluster crd",
			"ns",
			"uid",
			"apiVersion",
			listOf(
				customResourceDefinitionVersion("v42"),
				customResourceDefinitionVersion(version),
				customResourceDefinitionVersion("v84")),
			group,
			kind,
			CustomResourceScope.CLUSTER
		)
		// when
		val matching = isMatchingSpec(neo, crd)
		// then
		assertThat(matching).isTrue()
	}

	@Test
	fun `#isMatchingSpec should return false if crd has spec with different kind`() {
		// given
		val group = null
		val version = "v1"
		val neo = resource<HasMetadataResource>("neo", "zion", "uid", getApiVersion(group, version), "1")
		val crd = customResourceDefinition(
			"cluster crd",
			"ns",
			"uid",
			"apiVersion",
			listOf(customResourceDefinitionVersion(version)),
			group,
			"someOtherKind",
			CustomResourceScope.CLUSTER
		)
		// when
		val matching = isMatchingSpec(neo, crd)
		// then
		assertThat(matching).isFalse()
	}

	@Test
	fun `#isMatchingSpec should return false if crd has spec with different version`() {
		// given
		val group = null
		val version = "v1"
		val neo = resource<Pod>("neo", "zion", "uid", getApiVersion(group, version), "1")
		val kind = neo.kind
		val crd = customResourceDefinition(
			"cluster crd",
			"ns",
			"uid",
			"apiVersion",
			listOf(customResourceDefinitionVersion("v42")),
			group,
			kind,
			CustomResourceScope.CLUSTER
		)
		// when
		val matching = isMatchingSpec(neo, crd)
		// then
		assertThat(matching).isFalse()
	}

	@Test
	fun `#isWillBeDeleted should return true if setWillBeDeleted was called on the same resource `() {
		// given
		val meta = ObjectMetaBuilder().build()
		val neo = PodBuilder()
			.withMetadata(meta)
			.build()
		assertThat(isWillBeDeleted(neo)).isFalse()
		// when
		setWillBeDeleted(neo)
		// then
		assertThat(isWillBeDeleted(neo)).isTrue()
	}
}