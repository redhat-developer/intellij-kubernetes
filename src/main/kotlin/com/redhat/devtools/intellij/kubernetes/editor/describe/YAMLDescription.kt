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
package com.redhat.devtools.intellij.kubernetes.editor.describe

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.NONE
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedValue
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.util.stream.Collectors

class YAMLDescription : Description() {

	private val yaml = let {
		val options = DumperOptions().apply {
			defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
		}
		Yaml(options)
	}

	override fun toText(): String {
		val map = toMap(children)
		if (map.isEmpty()) {
			return ""
		}
		return yaml.dump(map)
	}

	/*
	 * Turning objects into map because I couldn't convince snakeyaml to format like I wanted by using native configurations.
	 */
	private fun toMap(paragraphs: List<Paragraph>): Map<String, Any?> {
		return paragraphs.stream()
			.filter { paragraph -> paragraph.title.isNotBlank() }
			.collect(
				Collectors.toMap(
					Paragraph::title,
					{ paragraph ->
						when {
							paragraph is NamedValue ->
								// dont NPE if there is no value
								paragraph.value ?: NONE

							paragraph is NamedSequence ->
								paragraph.children

							paragraph is Chapter ->
								toMap(paragraph.children)

							else ->
								paragraph ?: NONE
						}
					},
					{ existing: Any, _: Any -> existing },
					// keep ordering
					{ LinkedHashMap<String, Any>() }
				)
			)
	}
}