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
package com.redhat.devtools.intellij.kubernetes.model

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.redhat.devtools.intellij.common.compat.NotificationGroupFactory

class Notification {

	companion object {
		private val NOTIFICATION_GROUP = NotificationGroupFactory.create(
			"Kubernetes Notification Group", NotificationDisplayType.BALLOON, true)
	}

	fun error(title: String, content: String) {
		NOTIFICATION_GROUP
			.createNotification(content, NotificationType.ERROR)
			.apply { setTitle(title) }
			.notify(null)
	}

	fun info(title: String, content: String) {
		NOTIFICATION_GROUP
			.createNotification(content, NotificationType.INFORMATION)
			.apply { setTitle(title) }
			.notify(null)
	}

}