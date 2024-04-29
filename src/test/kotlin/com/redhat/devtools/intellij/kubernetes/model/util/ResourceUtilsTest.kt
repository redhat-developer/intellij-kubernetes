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
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry
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
	fun `#isOutdated should return false if both resources have same version`() {
		// given
		val sameVersion1 = resource<Pod>("neo", "ns","uid", "v1", "1")
		val sameVersion2 = resource<Pod>("neo", "ns","uid", "v1", "1")
		// when
		val newer = sameVersion1.isOutdated(sameVersion2)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isOutdated should return true if this resource has older version`() {
		// given
		val older = resource<Pod>("neo", "ns","uid", "v1", "1")
		val newer = resource<Pod>("neo", "ns","uid", "v1", "2")
		// when
		val isNewer = newer.isOutdated(older)
		// then
		assertThat(isNewer).isTrue()
	}

	@Test
	fun `#isOutdated should return true if given resource has newer version`() {
		// given
		val older = resource<Pod>("neo", "ns","uid", "v1", "1")
		val newer = resource<Pod>("neo", "ns", "uid", "v1", "2")
		// when
		val isNewer = older.isOutdated(newer)
		// then
		assertThat(isNewer).isFalse()
	}

	@Test
	fun `#isOutdated should return true if resource without version is compared to resource with version`() {
		// given
		val noVersion = resource<Pod>("neo", "ns","uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "ns","uid", "v1","1")
		// when
		val newer = noVersion.isOutdated(hasVersion)
		// then
		assertThat(newer).isTrue()
	}

	@Test
	fun `#isOutdated should return false if resource with version is compared to resource without version`() {
		// given
		val noVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "zion", "uid", "v1","1")
		// when
		val newer = hasVersion.isOutdated(noVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isOutdated should return false if both resources have no version`() {
		// given
		val noVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		val hasVersion = resource<Pod>("neo", "zion", "uid", "v1",null)
		// when
		val newer = hasVersion.isOutdated(noVersion)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isOutdated should return false if resources have different name`() {
		// given
		val neo = resource<Pod>("neo", "ns", "uid", "v1","1")
		val morpheus = resource<Pod>("morpheus", "ns", "uid", "v1","2")
		// when
		val newer = morpheus.isOutdated(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isOutdated should return false if resources have different namespace`() {
		// given
		val neo = resource<Pod>("neo", "matrix", "uid", "v1","1")
		val morpheus = resource<Pod>("neo", "zion", "uid", "v1","2")
		// when
		val newer = morpheus.isOutdated(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isOutdated should return false if resources have different api version`() {
		// given
		val neo = resource<Pod>("neo", "zion", "uid", "v1","1")
		val morpheus = resource<Pod>("neo", "zion", "uid", "v2","2")
		// when
		val newer = morpheus.isOutdated(neo)
		// then
		assertThat(newer).isFalse()
	}

	@Test
	fun `#isGreaterIntThan(String) should return true if string is larger than given string`() {
		// given
		val smaller = "1"
		val greater = "2"
		// when
		val isGreater = greater.isGreaterIntThan(smaller)
		// then
		assertThat(isGreater).isTrue()
	}

	@Test
	fun `#isGreaterIntThan(String) should return false if string is smaller than given string`() {
		// given
		val smaller = "1"
		val greater = "2"
		// when
		val isGreater = smaller.isGreaterIntThan(greater)
		// then
		assertThat(isGreater).isFalse()
	}

	@Test
	fun `#isGreaterIntThan(String) should return true if given string is null`() {
		// given
		val notNull = "1"
		val nullString = null
		// when
		val isGreater = notNull.isGreaterIntThan(nullString)
		// then
		assertThat(isGreater).isTrue()
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

	@Test
	fun `#runWithoutServerSetProperties should have NO uid NOR resourceVersion when running lambda`() {
		// given
		val withResourceVersion = PodBuilder()
			.withNewMetadata()
				.withUid("yoda")
				.withResourceVersion("42")
			.endMetadata()
			.build()
		assertThat(withResourceVersion.metadata.resourceVersion).isNotNull()
		// when
		var hasNoResourceVersionAndUid: Boolean? = null
		runWithoutServerSetProperties(withResourceVersion) {
			// has no resource version nor uid when running in this lambda
			hasNoResourceVersionAndUid = withResourceVersion.metadata.resourceVersion == null
					&& withResourceVersion.metadata.uid == null
			withResourceVersion
		}
		// then
		assertThat(hasNoResourceVersionAndUid).isTrue()
	}

	@Test
	fun `#runWithoutServerSetProperties should have resourceVersion & uid after lambda was run`() {
		// given
		val withResourceVersion = PodBuilder()
			.withNewMetadata()
			.withUid("yoda")
			.withResourceVersion("42")
			.endMetadata()
			.build()
		assertThat(withResourceVersion.metadata.resourceVersion).isNotNull()
		// when
		runWithoutServerSetProperties(withResourceVersion) {
			// has no resource version nor uid when running in this lambda
			withResourceVersion
		}
		// then
		assertThat(withResourceVersion.metadata.resourceVersion).isEqualTo("42")
		assertThat(withResourceVersion.metadata.uid).isEqualTo("yoda")
	}

	@Test
	fun `#runWithoutServerSetProperties(resource1, resource2) should have NO uid NOR resourceVersion when running lambda`() {
		// given
		val resource1 = PodBuilder()
			.withNewMetadata()
			.withUid("yoda")
			.withResourceVersion("42")
			.endMetadata()
			.build()
		assertThat(resource1.metadata.resourceVersion).isNotNull()
		val resource2 = PodBuilder()
			.withNewMetadata()
			.withUid("obiwan")
			.withResourceVersion("21")
			.endMetadata()
			.build()
		assertThat(resource2.metadata.resourceVersion).isNotNull()
		// when
		var resource1HasNoResourceVersionAndUid: Boolean? = null
		var resource2HasNoResourceVersionAndUid: Boolean? = null
		runWithoutServerSetProperties(resource1, resource2) {
			// has no resource version nor uid when running in this lambda
			resource1HasNoResourceVersionAndUid = resource1.metadata.resourceVersion == null
					&& resource1.metadata.uid == null
			resource2HasNoResourceVersionAndUid = resource2.metadata.resourceVersion == null
					&& resource2.metadata.uid == null
			resource1
		}
		// then
		assertThat(resource1HasNoResourceVersionAndUid).isTrue()
		assertThat(resource2HasNoResourceVersionAndUid).isTrue()
	}

	@Test
	fun `#runWithoutServerSetProperties(resource1, resource2) should have uid and resourceVersion after running lambda`() {
		// given
		val resource1 = PodBuilder()
			.withNewMetadata()
			.withUid("yoda")
			.withResourceVersion("42")
			.endMetadata()
			.build()
		assertThat(resource1.metadata.resourceVersion).isNotNull()
		val resource2 = PodBuilder()
			.withNewMetadata()
			.withUid("obiwan")
			.withResourceVersion("21")
			.endMetadata()
			.build()
		assertThat(resource2.metadata.resourceVersion).isNotNull()
		// when
		runWithoutServerSetProperties(resource1, resource2) {
			true
		}
		// then
		assertThat(resource1.metadata.resourceVersion).isEqualTo("42")
		assertThat(resource1.metadata.uid).isEqualTo("yoda")
		assertThat(resource2.metadata.resourceVersion).isEqualTo("21")
		assertThat(resource2.metadata.uid).isEqualTo("obiwan")
	}

	@Test
	fun `#hasManagedField should return true if resource has managed fields `() {
		// given
		val meta = ObjectMetaBuilder()
			.withManagedFields(ManagedFieldsEntry())
			.build()
		val neo = PodBuilder()
			.withMetadata(meta)
			.build()
		// when
		val hasManagedFields = hasManagedFields(neo)
		// then
		assertThat(hasManagedFields).isTrue()
	}

	@Test
	fun `#hasManagedField should return false if resource has empty list of managed fields `() {
		// given
		val meta = ObjectMetaBuilder()
			.build()
		meta.managedFields = emptyList()
		val neo = PodBuilder()
			.withMetadata(meta)
			.build()
		// when
		val hasManagedFields = hasManagedFields(neo)
		// then
		assertThat(hasManagedFields).isFalse()
	}

	@Test
	fun `#hasManagedField should return false if resource has no managed fields `() {
		// given
		val meta = ObjectMetaBuilder().build()
		val neo = PodBuilder()
			.withMetadata(meta)
			.build()
		// when
		val hasManagedFields = hasManagedFields(neo)
		// then
		assertThat(hasManagedFields).isFalse()
	}

}