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
package com.redhat.devtools.intellij.kubernetes.model.context

import com.redhat.devtools.intellij.kubernetes.model.util.toMessage
import io.fabric8.kubernetes.api.model.NamedContext

interface IContext {
	val active: Boolean
	val name: String?
	val namespace: String?
}

open class Context(protected val context: NamedContext): IContext {
	override val active: Boolean = false
	override val name: String?
		get() = context.name
	override val namespace: String?
		get() = context.context?.namespace
}

class KubeConfigError(error: Exception? = null): IContext {
	override val active: Boolean = false
	override val name: String = "Configuration error: ${toMessage(error)}"
	override val namespace: String? = null
}
