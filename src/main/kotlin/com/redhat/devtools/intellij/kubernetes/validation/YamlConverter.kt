/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.validation

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.yaml.psi.YAMLDocument
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.StringReader

object YamlConverter {

    private val parser = Yaml()

    fun toJson(yamlDocument: YAMLDocument): JSONObject? {
        val yamlString = yamlDocument.text
        if (yamlString.isBlank()) {
            return null
        }

        return try {
            val javaMap = parser.load(StringReader(yamlString)) as? Map<String, Any>
            if (javaMap != null) {
                JSONObject(javaMap)
            } else {
                null
            }
        } catch (e: Exception) {
            logger<YamlConverter>().info("Could not parse YAML to JSON", e)
            null
        }
    }
}