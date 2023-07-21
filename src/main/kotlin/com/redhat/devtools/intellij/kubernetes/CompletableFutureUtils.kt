/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes

import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.Executor

object CompletableFutureUtils {
    val UI_EXECUTOR = Executor { runnable: Runnable ->
        ApplicationManager.getApplication().invokeLater { runnable.run() }
    }

    val PLATFORM_EXECUTOR = Executor { runnable: Runnable ->
        ApplicationManager.getApplication().executeOnPooledThread { runnable.run() }
    }
}
