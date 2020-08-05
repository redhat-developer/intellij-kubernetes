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
package org.jboss.tools.intellij.kubernetes.model.resource.kubernetes.custom

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.ListOptionsBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.Watchable
import java.io.IOException

class GenericResourceWatchable(private val watchSupplier: (options: ListOptions, resourceWatcher: DelegatingResourceWatcher) -> Watch?)
	: Watchable<Watch, Watcher<GenericResource>> {

	override fun watch(watcher: Watcher<GenericResource>): Watch? {
		return watch(ListOptionsBuilder().build(), watcher)
	}

	override fun watch(resourceVersion: String?, watcher: Watcher<GenericResource>): Watch? {
		return watch(ListOptionsBuilder().withResourceVersion(resourceVersion).build(), watcher)
	}

	override fun watch(options: ListOptions, watcher: Watcher<GenericResource>): Watch? {
		return watchSupplier.invoke(options, DelegatingResourceWatcher(watcher))
	}

	/**
	 * Watcher that delegates events to a given target watcher.
	 * This watcher receives resources in an event as json strings.
	 * It then deserializes those json string to GenericCustomResource(s)
	 */
	class DelegatingResourceWatcher(private val target: Watcher<GenericResource>) : Watcher<String> {

		override fun eventReceived(action: Watcher.Action, resource: String) {
			val customResource = createGenericCustomResource(resource)
			target.eventReceived(action, customResource)
		}

		private fun createGenericCustomResource(json: String): GenericResource {
			val mapper = ObjectMapper()
			val module = SimpleModule()
			module.addDeserializer(GenericResource::class.java, GenericCustomResourceDeserializer())
			mapper.registerModule(module)

			return mapper.readValue(json, GenericResource::class.java)
		}

		override fun onClose(exception: KubernetesClientException?) {
		}

		inner class GenericCustomResourceDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<GenericResource?>(vc) {
			@Throws(IOException::class, JsonProcessingException::class)

			override fun deserialize(parser: JsonParser, ctx: DeserializationContext?): GenericResource {
				return GenericResourceFactory.createResource(parser.codec.readTree(parser))
			}
		}
	}
}

