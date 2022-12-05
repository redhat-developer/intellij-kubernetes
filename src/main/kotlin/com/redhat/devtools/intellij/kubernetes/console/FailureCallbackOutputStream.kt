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
package com.redhat.devtools.intellij.kubernetes.console

import com.intellij.execution.ui.ConsoleView

class FailureCallbackOutputStream(private val onFailure: (message: String) -> Unit, terminal: ConsoleView): ConsoleOutputStream(terminal) {

    companion object {
        private val failureExpression = Regex("\"kind\":\"Status\".+\"status\":\"Failure\".+\"message\":(\"[^,]+)")
    }

    override fun processOutput(output: String): String {
        val matches = failureExpression.find(output)
        val message = matches?.groups?.get(1)?.value
        if (message != null) {
            onFailure.invoke(cleanup(message))
        }
        return output
    }

    private fun cleanup(message: String): String {
        val cleaned = message.replace("\"", "") // remove "
        return cleaned.replace("\\", "") // remove \
    }
}