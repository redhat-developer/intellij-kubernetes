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
package com.redhat.devtools.intellij.kubernetes.model.util

import io.fabric8.kubernetes.client.KubernetesClientException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.UnknownHostException

class ExceptionUtilsTest {

	@Test
	fun `#getMessage should return 'No valid current context'`() {
		// given
		val e = UnknownHostException("https://kubernetes.default.svc")
		// when
		val message = toMessage(e)
		// then
		assertThat(message).startsWith("No valid current context")
	}

	@Test
	fun `#getMessage should return 'Unreachable cluster at'`() {
		// given
		val e = UnknownHostException("java.net.UnknownHostException: yoda.io")
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Unreachable cluster at yoda.io.")
	}

	@Test
	fun `#getMessage should return 'Unreachable cluster' for UnknownHostException`() {
		// given
		val e = UnknownHostException("java.net.UnknownHostException")
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Unreachable cluster.")
	}

	@Test
	fun `#getMessage should return 'Unauthorized verify' for KubernetesClientException with http unauthorized code`() {
		// given
		val e = IOException(ResourceException("", KubernetesClientException(null, HttpURLConnection.HTTP_UNAUTHORIZED, null)))
		// when
		val message = toMessage(e)
		// then
		assertThat(message).startsWith("Unauthorized. Verify")
	}

	@Test
	fun `#getMessage should return portion after 'Message' in effective exception message`() {
		// given
		val e = IOException(null, ResourceException(null, KubernetesClientException("Failure executing: " +
				"GET at: https://api.sandbox-m3.1530.p1.openshiftapps.com:6443/apis/project.openshift.io/v1/projects. " +
				"Message: Unauthorized! Token may have expired! Please log-in again. Unauthorized.")))
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Unauthorized! Token may have expired! Please log-in again. Unauthorized.")
	}

	@Test
	fun `#getMessage should return cause message in KubernetesClientException with 'Operation'`() {
		// given
		val e = IOException(
			null,
			ResourceException(
				null,
				KubernetesClientException(
					"Operation: [list]  for kind: [Service]  with name: [null]  in namespace: [default]  failed.",
					KubernetesClientException(
						"Failure executing: " +
								"GET at: https://api.sandbox-m3.1530.p1.openshiftapps.com:6443/apis/project.openshift.io/v1/projects. " +
								"Message: Unauthorized! Token may have expired! Please log-in again. Unauthorized."
					)
				)
			)
		)
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Unauthorized! Token may have expired! Please log-in again. Unauthorized.")
	}


	@Test
	fun `#getMessage should return effective exception message`() {
		// given
		val e = IOException(null, ResourceException(null, KubernetesClientException("Failure executing: " +
				"GET at: https://api.sandbox-m3.1530.p1.openshiftapps.com:6443/apis/project.openshift.io/v1/projects. " +
				"Because cluster is dizzy")))
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Failure executing: " +
				"GET at: https://api.sandbox-m3.1530.p1.openshiftapps.com:6443/apis/project.openshift.io/v1/projects. " +
				"Because cluster is dizzy")
	}

	@Test
	fun `#getMessage should return 'Unknown Error' if no message is present`() {
		// given
		val e = IOException(null, ResourceException(null, KubernetesClientException(null, null)))
		// when
		val message = toMessage(e)
		// then
		assertThat(message).isEqualTo("Unknown Error.")
	}
}