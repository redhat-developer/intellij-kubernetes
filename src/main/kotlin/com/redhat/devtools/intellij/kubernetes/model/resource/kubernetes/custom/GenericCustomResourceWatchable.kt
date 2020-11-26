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
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes.custom

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.ListOptionsBuilder
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import io.fabric8.kubernetes.client.dsl.Watchable

class GenericCustomResourceWatchable(private val watchSupplier: (options: ListOptions, resourceWatcher: DelegatingResourceWatcher) -> Watch?)
	: Watchable<Watcher<GenericCustomResource>> {

	override fun watch(watcher: Watcher<GenericCustomResource>): Watch? {
		return watch(ListOptionsBuilder().build(), watcher)
	}

	override fun watch(resourceVersion: String?, watcher: Watcher<GenericCustomResource>): Watch? {
		return watch(ListOptionsBuilder().withResourceVersion(resourceVersion).build(), watcher)
	}

	override fun watch(options: ListOptions, watcher: Watcher<GenericCustomResource>): Watch? {
		return watchSupplier.invoke(options, DelegatingResourceWatcher(watcher))
	}

	/**
	 * Watcher that delegates events to a given target watcher.
	 * This watcher receives resources in an event as json strings.
	 * It then deserializes those json string to GenericCustomResource(s)
	 */
	class DelegatingResourceWatcher(private val target: Watcher<GenericCustomResource>) : Watcher<String> {

		private val mapper = ObjectMapper()

		override fun eventReceived(action: Watcher.Action, resource: String) {
			val customResource = mapper.readValue(resource, GenericCustomResource::class.java)
			target.eventReceived(action, customResource)
		}

		override fun onClose(e: WatcherException?) {
			logger<DelegatingResourceWatcher>().debug("Watcher $target was closed.", e)
		}
	}
}
