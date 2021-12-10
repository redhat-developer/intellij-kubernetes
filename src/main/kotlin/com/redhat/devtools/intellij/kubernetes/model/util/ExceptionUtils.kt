package com.redhat.devtools.intellij.kubernetes.model.util

fun causeOrExceptionMessage(e: Throwable, prefix: String = ""): String {
    val message = e.cause?.message ?: e.message
    return if (message != null) {
        prefix + message
    } else {
        ""
    }
}