/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.client

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class KubernetesRelated {

    companion object Constants {
        const val separator = "\n"

        @JvmStatic
        val ready: (bin: String) -> Boolean = {
            val pb = ProcessBuilder(it, "version")
            pb.environment().putAll(System.getenv())
            val process = pb.start()
            process.waitFor()
            0 == process.exitValue()
        }

        @JvmStatic
        fun command(
            bin: String,
            cmd: String,
            structured: Boolean = false,
            kind: String? = null,
            name: String? = null,
            namespace: String? = null,
            options: List<String>? = null,
            stdin: ByteArray? = null
        ): String = command(bin, listOf(cmd), structured, kind, name, namespace, options, stdin)

        @JvmStatic
        fun command(
            bin: String,
            cmd: List<String>,
            structured: Boolean = false,
            kind: String? = null,
            name: String? = null,
            namespace: String? = null,
            options: List<String>? = null,
            stdin: ByteArray? = null
        ): String {
            val commands = ArrayList<String>()
            commands.add(bin)
            commands.addAll(cmd)
            if (null != kind) {
                commands.add(kind)
            }
            if (null != name) {
                commands.add(name)
            }
            if (null != namespace) {
                commands.add("-n")
                commands.add(namespace)
            }
            if (structured) {
                commands.add("-o")
                commands.add("json")
            }
            if (null != options) {
                commands.addAll(options)
            }
            val pb = ProcessBuilder(commands)
            pb.environment().putAll(System.getenv())
            val process: Process = pb.start()
            if (null != stdin) process.outputStream.write(stdin)
            process.waitFor(60, TimeUnit.SECONDS)
            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            return lines.joinToString(separator)
        }
    }
}