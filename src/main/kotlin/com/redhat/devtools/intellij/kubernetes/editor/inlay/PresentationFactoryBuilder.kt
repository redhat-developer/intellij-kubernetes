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
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor

/**
 * A factory that creates a [PresentationFactory]. This class bridges the difference in API between
 * <= IC-2022.3 and above.
 */
object PresentationFactoryBuilder {
	fun build(editor: Editor): PresentationFactory? {
		try {
			val constructor = PresentationFactory::class.java.constructors.firstOrNull() ?: return null
			// IC-2022.3: PresentationFactory(EditorImpl), > IC-2022.3: PresentationFactory(Editor)
			return constructor.newInstance(editor) as PresentationFactory?
		} catch (e: Exception) {
			return null
		}
	}
}