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

import com.intellij.openapi.project.Project
import com.nhaarman.mockitokotlin2.mock
import com.redhat.devtools.intellij.kubernetes.model.IResourceModel
import com.redhat.devtools.intellij.kubernetes.model.mocks.Fakes.pod
import com.redhat.devtools.intellij.kubernetes.tree.TreeStructure.ResourceDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DescriptorTest {

    private val model: IResourceModel = mock()
    private val project: Project = mock()
    private val lukyLuke = pod("Luky Luke")
    private val lukyLukeWithTimestamp = pod("Luky Luke").apply {
        metadata.creationTimestamp = "42"
    }
    private val jollyJumper = pod("Jolly Jumper")

    @Test
    fun`ResourceDescriptor#hasElement should return true if is same resource`() {
        // given
        val descriptor = ResourceDescriptor(lukyLuke, null, null,model, project)
        // when
        val hasElement = descriptor.hasElement(lukyLuke)
        // then
        assertThat(hasElement).isTrue()
    }

    @Test
    fun`ResourceDescriptor#hasElement should return true if is same resource with different timestamp`() {
        // given
        val descriptor = ResourceDescriptor(lukyLuke, null, null,model, project)
        // when
        val hasElement = descriptor.hasElement(lukyLukeWithTimestamp)
        // then
        assertThat(hasElement).isTrue()
    }

    @Test
    fun`ResourceDescriptor#hasElement should return false if is different resource`() {
        // given
        val descriptor = ResourceDescriptor(lukyLuke, null, null,model, project)
        // when
        val hasElement = descriptor.hasElement(jollyJumper)
        // then
        assertThat(hasElement).isFalse()
    }
}