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
package com.redhat.devtools.intellij.kubernetes.tree

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.mocks.Mocks.resourceModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TreeStructureExtensionTest {

	private val model: IResourceModel = resourceModel()
	private val extensionPoint: ExtensionPointName<ITreeStructureContributionFactory> = mock()
	private val project: Project = mock()
	private val structure: TreeStructure = TestableTreeStructure(project, model, extensionPoint)

	@Test
	fun `#getChildElements should only return children of extensions that can contribute`() {
		// given
		val nonContributing = structureContribution(false)
		val contributing = structureContribution(true)
		mockExtensionList(nonContributing, contributing)
		// when
		structure.getChildElements(mock())
		// then
		verify(nonContributing, never()).getChildElements(any())
		verify(contributing, times(1)).getChildElements(any())
	}

	@Test
	fun `#getChildElements returns exception element if contribution throws`() {
		// given
		val contributing = structureContribution(true, children = RuntimeException())
		mockExtensionList(contributing)
		// when
		val children = structure.getChildElements(mock())
		// then
		assertThat(children).hasAtLeastOneElementOfType(java.lang.Exception::class.java)
	}

	@Test
	fun `#getChildElements returns elements even if a contribution throws`() {
		// given
		val contributing1 = structureContribution(true, children = RuntimeException())
		val contributing2 = structureContribution(true, listOf<Any>(mock(), mock()))
		val contributing3 = structureContribution(true, listOf<Any>(mock()))
		mockExtensionList(contributing1, contributing2, contributing3)
		// when
		structure.getChildElements(mock())
		// then
		verify(contributing1).getChildElements(any())
		verify(contributing2).getChildElements(any())
		verify(contributing3).getChildElements(any())
	}

	@Test
	fun `#getParentElement should only return parent from extensions that can contribute`() {
		// given
		val nonContributing = structureContribution(false)
		val contributing = structureContribution(true)
		mockExtensionList(nonContributing, contributing)
		// when
		structure.getParentElement(mock())
		// then
		verify(nonContributing, never()).getParentElement(any())
		verify(contributing, times(1)).getParentElement(any())
	}

	@Test
	fun `#getParentElement should return 1st parent returned by 1st contribution, ignore others`() {
		// given
		val contributing1 = structureContribution(true, parent = mock())
		val contributing2 = structureContribution(true, parent = mock())
		mockExtensionList(contributing1, contributing2)
		// when
		structure.getParentElement(mock())
		// then
		verify(contributing1, times(1)).getParentElement(any())
		verify(contributing2, never()).getParentElement(any())
	}

	@Test
	fun `#getParentElement should ignore parent with null value`() {
		// given
		val contributing1 = structureContribution(true, parent = null)
		val contributing2 = structureContribution(true, parent = mock())
		mockExtensionList(contributing1, contributing2)
		// when
		structure.getParentElement(mock())
		// then should query contribution2 since contribution1 returns null
		verify(contributing1).getParentElement(any())
		verify(contributing2).getParentElement(any())
	}

	@Test
	fun `#getParentElement should ignore contributor that throws`() {
		// given
		val contributing1 = structureContribution(true, parent = RuntimeException())
		val contributing2 = structureContribution(true, parent = mock())
		mockExtensionList(contributing1, contributing2)
		// when
		structure.getParentElement(mock())
		// then should query contribution2 since contribution1 returns null
		verify(contributing1).getParentElement(any())
		verify(contributing2).getParentElement(any())
	}

	private fun structureContribution(canContribute: Boolean, children: Any = emptyList<Any>(), parent: Any? = null)
			: ITreeStructureContribution {
		return mock {
			on { canContribute() } doReturn canContribute
			on { getChildElements(any()) } doAnswer {
				@Suppress("UNCHECKED_CAST")
				when (children) {
					// throw exception
					is Exception -> throw children
					// return children as collection
					is Collection<*> -> children as Collection<Any>
					// default: return empty list
					else -> emptyList()
				}
			}
			on { getParentElement(any()) } doAnswer {
				when(parent) {
					is Exception -> throw parent
					else -> parent
				}
			}
		}
	}

	private fun structureContributionFactory(contribution: ITreeStructureContribution): ITreeStructureContributionFactory {
		return object: ITreeStructureContributionFactory {
			override fun create(model:IResourceModel): ITreeStructureContribution {
				return contribution
			}
		}
	}

	private fun mockExtensionList(vararg contributions: ITreeStructureContribution) {
		whenever(extensionPoint.extensionList)
				.doReturn(contributions
						.map { structureContributionFactory(it) }
						.toList())
	}

	class TestableTreeStructure(project: Project, model: IResourceModel, extensionPoint: ExtensionPointName<ITreeStructureContributionFactory>)
		: TreeStructure(project, model, extensionPoint) {

		public override fun getTreeStructureDefaults(model: IResourceModel): List<ITreeStructureContribution> {
			return emptyList()
		}
	}

}