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
package com.redhat.devtools.intellij.kubernetes.editor.util

import com.redhat.devtools.intellij.common.validation.KubernetesTypeInfo

private const val KIND_DEPLOYMENT = "Deployment"
private const val KIND_CRON_JOB = "CronJob"
private const val KIND_DAEMON_SET = "DaemonSet"
private const val KIND_JOB = "Job"
private const val KIND_NETWORK_POLICY = "NetworkPolicy"
private const val KIND_PERSISTENT_VOLUME = "PersistentVolume"
private const val KIND_PERSISTENT_VOLUME_CLAIM = "PersistentVolumeClaim"
private const val KIND_POD = "Pod"
private const val KIND_POD_DISRUPTION_BUDGET = "PodDisruptionBudget"
private const val KIND_REPLICATION_CONTROLLER = "ReplicationController"
private const val KIND_REPLICA_SET = "ReplicaSet"
private const val KIND_SERVICE = "Service"
private const val KIND_STATEFUL_SET = "StatefulSet"

fun KubernetesTypeInfo.isCronJob(): Boolean {
    return this.kind == KIND_CRON_JOB
}

fun KubernetesTypeInfo.isDaemonSet(): Boolean {
    return this.kind == KIND_DAEMON_SET
}

fun KubernetesTypeInfo.isDeployment(): Boolean {
    return this.kind == KIND_DEPLOYMENT
}

fun KubernetesTypeInfo.isJob(): Boolean {
    return this.kind == KIND_JOB
}

fun KubernetesTypeInfo.isNetworkPolicy(): Boolean {
    return this.kind == KIND_NETWORK_POLICY
}

fun KubernetesTypeInfo.isPersistentVolume(): Boolean {
    return this.kind == KIND_PERSISTENT_VOLUME
}

fun KubernetesTypeInfo.isPersistentVolumeClaim(): Boolean {
    return this.kind == KIND_PERSISTENT_VOLUME_CLAIM
}

fun KubernetesTypeInfo.isPod(): Boolean {
    return this.kind == KIND_POD
}

fun KubernetesTypeInfo.isPodDisruptionBudget(): Boolean {
    return this.kind == KIND_POD_DISRUPTION_BUDGET
}

fun KubernetesTypeInfo.isReplicationController(): Boolean {
    return this.kind == KIND_REPLICATION_CONTROLLER
}

fun KubernetesTypeInfo.isReplicaSet(): Boolean {
    return this.kind == KIND_REPLICA_SET
}

fun KubernetesTypeInfo.isService(): Boolean {
    return this.kind == KIND_SERVICE
}

fun KubernetesTypeInfo.isStatefulSet(): Boolean {
    return this.kind == KIND_STATEFUL_SET
}