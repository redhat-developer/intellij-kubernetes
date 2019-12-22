package org.jboss.tools.intellij.kubernetes.model.mocks

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta

object Mocks {
        val NAMESPACE1 = mockNamespace("namespace1")
        val NAMESPACE2 = mockNamespace("namespace2")
        val NAMESPACE3 = mockNamespace("namespace3")

        private fun mockNamespace(name: String): Namespace {
            val metadata = mock<ObjectMeta> {
                on { getName() } doReturn name
            }
            return mock {
                on { getMetadata() } doReturn metadata
            }
        }
    }