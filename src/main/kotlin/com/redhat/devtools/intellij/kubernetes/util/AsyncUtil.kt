/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.util

import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Runs the given runnable async and returns a promise holding the return value.
 * This is needed because the platform version of this is inlined which errors compiling when using target 1.8.
 *
 * @param runnable the runnable to run async
 * @return promise with the result
 *
 * @see Promise
 * @see [AppExecutorUtil.getAppExecutorService]
 */
fun <T> runAsync(runnable: () -> T): Promise<T> {
    val promise = AsyncPromise<T>()
    AppExecutorUtil.getAppExecutorService().execute {
        val result = try {
            runnable()
        }
        catch (e: Throwable) {
            promise.setError(e)
            return@execute
        }
        promise.setResult(result)
    }
    return promise
}
