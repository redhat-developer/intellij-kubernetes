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
package com.redhat.devtools.intellij.kubernetes.editor.describe.describer

import com.redhat.devtools.intellij.kubernetes.editor.describe.DescriptionConstants.Values.UNSET
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Chapter
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.NamedSequence
import com.redhat.devtools.intellij.kubernetes.editor.describe.paragraphs.Paragraph
import io.fabric8.kubernetes.api.model.AWSElasticBlockStoreVolumeSource
import io.fabric8.kubernetes.api.model.AzureDiskVolumeSource
import io.fabric8.kubernetes.api.model.AzureFileVolumeSource
import io.fabric8.kubernetes.api.model.CSIVolumeSource
import io.fabric8.kubernetes.api.model.CephFSVolumeSource
import io.fabric8.kubernetes.api.model.CinderVolumeSource
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource
import io.fabric8.kubernetes.api.model.DownwardAPIVolumeSource
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource
import io.fabric8.kubernetes.api.model.EphemeralVolumeSource
import io.fabric8.kubernetes.api.model.FCVolumeSource
import io.fabric8.kubernetes.api.model.FlexVolumeSource
import io.fabric8.kubernetes.api.model.FlockerVolumeSource
import io.fabric8.kubernetes.api.model.GCEPersistentDiskVolumeSource
import io.fabric8.kubernetes.api.model.GitRepoVolumeSource
import io.fabric8.kubernetes.api.model.GlusterfsVolumeSource
import io.fabric8.kubernetes.api.model.ISCSIVolumeSource
import io.fabric8.kubernetes.api.model.NFSVolumeSource
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
import io.fabric8.kubernetes.api.model.PhotonPersistentDiskVolumeSource
import io.fabric8.kubernetes.api.model.PortworxVolumeSource
import io.fabric8.kubernetes.api.model.ProjectedVolumeSource
import io.fabric8.kubernetes.api.model.QuobyteVolumeSource
import io.fabric8.kubernetes.api.model.RBDVolumeSource
import io.fabric8.kubernetes.api.model.ScaleIOVolumeSource
import io.fabric8.kubernetes.api.model.SecretVolumeSource
import io.fabric8.kubernetes.api.model.StorageOSVolumeSource
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VsphereVirtualDiskVolumeSource
import java.math.BigDecimal

class VolumesDescriber(private val volumes: List<Volume>): Describer {

	companion object {
		const val TITLE_TYPE = "Type"
	}

	override fun addTo(chapter: Chapter): Chapter {
		chapter.addChapter("Volumes", createVolumes(volumes))
		return chapter
	}

	private fun createVolumes(volumes: List<Volume>?): List<Paragraph> {
		if (volumes.isNullOrEmpty()) {
			return emptyList()
		}
		return volumes.mapNotNull { volume -> createVolume(volume) }
	}

	private fun createVolume(volume: Volume): Paragraph? {
		if (volume.name == null) {
			return null
		}
		val paragraph = Chapter(volume.name)
		when {
			volume.hostPath != null ->
				addHostPath(volume, paragraph)

			volume.emptyDir != null ->
				addEmptyDir(volume.emptyDir, paragraph)

			volume.gcePersistentDisk != null ->
				addGcePersistentDisk(volume.gcePersistentDisk, paragraph)

			volume.awsElasticBlockStore != null ->
				addAwsElasticBlockStore(volume.awsElasticBlockStore, paragraph)

			volume.gitRepo != null ->
				addGitRepo(volume.gitRepo, paragraph)

			volume.secret != null ->
				addSecret(volume.secret, paragraph)

			volume.configMap != null ->
				addConfigMap(volume.configMap, paragraph)

			volume.nfs != null ->
				addNfs(volume.nfs, paragraph)

			volume.iscsi != null ->
				addIscsi(volume.iscsi, paragraph)

			volume.glusterfs != null ->
				addGlusterfs(volume.glusterfs, paragraph)

			volume.persistentVolumeClaim != null ->
				addPersistentVolumeClaim(volume.persistentVolumeClaim, paragraph)

			volume.ephemeral != null ->
				addEphemeral(volume.ephemeral, paragraph)

			volume.rbd != null ->
				addRbd(volume.rbd, paragraph)

			volume.quobyte != null ->
				addQuobyte(volume.quobyte, paragraph)

			volume.downwardAPI != null ->
				addDownwardAPI(volume.downwardAPI, paragraph)

			volume.azureDisk != null ->
				addAzureDisk(volume.azureDisk, paragraph)

			volume.vsphereVolume != null ->
				addVsphereVolume(volume.vsphereVolume, paragraph)

			volume.cinder != null ->
				addCinder(volume.cinder, paragraph)

			volume.photonPersistentDisk != null ->
				addPhotoPersistentDisk(volume.photonPersistentDisk, paragraph)

			volume.portworxVolume != null ->
				addPortworxVolume(volume.portworxVolume, paragraph)

			volume.scaleIO != null ->
				addScaleIO(volume.scaleIO, paragraph)

			volume.cephfs != null ->
				addCephfs(volume.cephfs, paragraph)

			volume.storageos != null ->
				addStorageos(volume.storageos, paragraph)

			volume.fc != null ->
				addFc(volume.fc, paragraph)

			volume.azureFile != null ->
				addAzureFile(volume.azureFile, paragraph)

			volume.flexVolume != null ->
				addFlexVolume(volume.flexVolume, paragraph)

			volume.flocker != null ->
				addFlocker(volume.flocker, paragraph)

			volume.projected != null ->
				addProjected(volume.projected, paragraph)

			volume.csi != null ->
				addCsi(volume.csi, paragraph)

			else ->
				paragraph.addIfExists(TITLE_TYPE, "unknown")
		}
		return paragraph
	}

	private fun addCsi(csi: CSIVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "CSI (a Container Storage Interface (CSI) volume source)")
			.add("Driver", csi.driver)
			.add("FSType", csi.fsType)
			.add("ReadOnly", csi.readOnly)
	}

	private fun addHostPath(volume: Volume, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "HostPath (bare host directory volume)")
			.add("Path", volume.hostPath.path)
			.add("HostPathType", volume.hostPath.type)
	}

	private fun addEmptyDir(emptyDir: EmptyDirVolumeSource, parent: Chapter) {
		val sizeLimit = if (emptyDir.sizeLimit?.numericalAmount != null
			&& emptyDir.sizeLimit.numericalAmount > BigDecimal(0)
		) {
			emptyDir.sizeLimit
		} else {
			UNSET
		}
		parent.addIfExists(TITLE_TYPE, "EmptyDir (a temporary directory that shares a pod's lifetime)")
			.add("Medium", emptyDir.medium)
			.add("SizeLimit", sizeLimit.toString())
	}

	private fun addGcePersistentDisk(gce: GCEPersistentDiskVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "GCEPersistentDisk (a Persistent Disk resource in Google Compute Engine)")
			.add("PDName", gce.pdName)
			.add("FSType", gce.fsType)
			.add("Partition", gce.partition)
			.add("Readonly", gce.readOnly)
	}

	private fun addAwsElasticBlockStore(aws: AWSElasticBlockStoreVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "AWSElasticBlockStore (a Persistent Disk resource in AWS)")
			.add("VolumeID", aws.volumeID)
			.add("FSType", aws.fsType)
			.add("Partition", aws.partition)
			.add("ReadOnly", aws.readOnly)
	}

	private fun addGitRepo(git: GitRepoVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "GitRepo (a volume that is pulled from git when the pod is created)")
			.add("Repository", git.repository)
			.add("Revision", git.revision)
	}

	private fun addSecret(secret: SecretVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Secret (a volume populated by a Secret)")
			.add("SecretName", secret.secretName)
			.add("Optional", secret.optional)
	}

	private fun addConfigMap(configMap: ConfigMapVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "ConfigMap (a volume populated by a ConfigMap)")
			.add("Name", configMap.name)
			.add("Optional", configMap.optional)
	}

	private fun addNfs(nfs: NFSVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "NFS (an NFS mount that lasts the lifetime of a pod)")
			.add("Server", nfs.server)
			.add("Path", nfs.path)
			.add("ReadOnly", nfs.readOnly)
	}

	private fun addIscsi(iscsi: ISCSIVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "ISCSI (an ISCSI Disk resource that is attached to a kubelet's host machine and then exposed to the pod)")
			.add("TargetPortal", iscsi.targetPortal)
			.add("IQN", iscsi.iqn)
			.add("Lun", iscsi.lun)
			.add("ISCSIInterface", iscsi.iscsiInterface)
			.add("FSType", iscsi.fsType)
			.add("ReadOnly", iscsi.readOnly)
			.add("Portals", iscsi.portals?.joinToString(","))
			.add("DiscoveryCHAPAuth", iscsi.chapAuthDiscovery)
			.add("SecretRef", iscsi.secretRef?.name)
			.add("InitiatorName", iscsi.initiatorName)
	}

	private fun addGlusterfs(gfs: GlusterfsVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Glusterfs (a Glusterfs mount on the host that shares a pod's lifetime)")
			.add("EndpointsName", gfs.endpoints)
			.add("Path", gfs.path)
			.add("ReadOnly", gfs.readOnly)
	}

	private fun addPersistentVolumeClaim(pvc: PersistentVolumeClaimVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "PersistentVolumeClaim (a reference to a PersistentVolumeClaim in the same namespace)")
			.add("ClaimName", pvc.claimName)
			.add("ReadOnly", pvc.readOnly)
	}

	private fun addEphemeral(ephemeral: EphemeralVolumeSource, parent: Chapter) {
		parent.addIfExists(
			TITLE_TYPE,
			"EphemeralVolume (an inline specification for a volume that gets created and deleted with the pod)"
		)
		PersistentVolumeClaimDescriber(ephemeral.volumeClaimTemplate).addTo(parent)
	}

	private fun addRbd(rbd: RBDVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "RBD (a Rados Block Device mount on the host that shares a pod's lifetime)")
			.add("CephMonitors", rbd.monitors?.joinToString(", "))
			.add("RBDImage", rbd.image)
			.add("FSType", rbd.fsType)
			.add("RBDPool", rbd.pool)
			.add("RadosUser", rbd.user)
			.add("Keyring", rbd.keyring)
			.add("SecretRef", rbd.secretRef?.name)
			.add("ReadOnly", rbd.readOnly)
	}

	private fun addQuobyte(quobyte: QuobyteVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Quobyte (a Quobyte mount on the host that shares a pod's lifetime)")
			.add("Registry", quobyte.registry)
			.add("Volume", quobyte.volume)
			.add("ReadOnly", quobyte.readOnly)
	}

	private fun addDownwardAPI(downwardAPI: DownwardAPIVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "HostPath (bare host directory volume)")
			.addIfExists(NamedSequence("Mappings").addIfExists(
				downwardAPI.items.mapNotNull { file ->
					when {
						file.fieldRef != null ->
							"${file.fieldRef.fieldPath} -> ${file.path}"

						file.resourceFieldRef != null ->
							"${file.resourceFieldRef.resource} -> ${file.path}"

						else ->
							null
					}
				}
			))
	}

	private fun addAzureDisk(disk: AzureDiskVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "AzureDisk (an Azure Data Disk mount on the host and bind mount to the pod)")
			.add("DiskName", disk.diskName)
			.add("DiskURI", disk.diskURI)
			.add("Kind", disk.kind)
			.add("FSType", disk.fsType)
			.add("CachingMode", disk.cachingMode)
			.add("ReadOnly", disk.readOnly)
	}

	private fun addVsphereVolume(volume: VsphereVirtualDiskVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "SphereVolume (a Persistent Disk resource in vSphere)")
			.add("VolumePath", volume.volumePath)
			.add("FSType", volume.fsType)
			.add("StoragePolicyName", volume.storagePolicyName)
	}

	private fun addCinder(cinder: CinderVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Cinder (a Persistent Disk resource in OpenStack)")
			.add("VolumeID", cinder.volumeID)
			.add("FSType", cinder.fsType)
			.add("ReadOnly", cinder.readOnly)
			.add("SecretRef", cinder.secretRef?.name)
	}

	private fun addPhotoPersistentDisk(disk: PhotonPersistentDiskVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "PhotonPersistentDisk (a Persistent Disk resource in photon platform)")
			.add("PdID", disk.pdID)
			.add("FSType", disk.fsType)
	}

	private fun addPortworxVolume(volume: PortworxVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "PortworxVolume (a Portworx Volume resource)")
			.add("VolumeID", volume.volumeID)
	}

	private fun addScaleIO(scaleIO: ScaleIOVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "ScaleIO (a persistent volume backed by a block device in ScaleIO)")
			.add("Gateway", scaleIO.gateway)
			.add("System", scaleIO.system)
			.add("Protection Domain", scaleIO.protectionDomain)
			.add("Storage Pool", scaleIO.storagePool)
			.add("Storage Mode", scaleIO.storageMode)
			.add("VolumeName", scaleIO.volumeName)
			.add("FSType", scaleIO.fsType)
			.add("System", scaleIO.system)
			.add("ReadOnly", scaleIO.readOnly)
	}

	private fun addCephfs(cephfs: CephFSVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "CephFS (a CephFS mount on the host that shares a pod's lifetime)")
			.add("Monitors", cephfs.monitors?.joinToString(", "))
			.add("Path", cephfs.path)
			.add("User", cephfs.user)
			.add("SecretFile", cephfs.secretFile)
			.add("SecretRef", cephfs.secretRef?.name)
			.add("ReadOnly", cephfs.readOnly)
	}

	private fun addStorageos(storageos: StorageOSVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "StorageOS (a StorageOS Persistent Disk resource)")
			.add("VolumeName", storageos.volumeName)
			.add("VolumeNamespace", storageos.volumeNamespace)
			.add("FSType", storageos.fsType)
			.add("ReadOnly", storageos.readOnly)
	}

	private fun addFc(fc: FCVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "FC (a Fibre Channel disk)")
			.add("TargetWWNs", fc.targetWWNs?.joinToString(", "))
			.add("LUN", fc.lun?.toString(10))
			.addIfExists("FSType", fc.fsType)
			.addIfExists("ReadOnly", fc.readOnly)
	}

	private fun addAzureFile(file: AzureFileVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "AzureFile (an Azure File Service mount on the host and bind mount to the pod)")
			.addIfExists("SecretName", file.secretName)
			.addIfExists("ShareName", file.shareName)
			.addIfExists("ReadOnly", file.readOnly)
	}

	private fun addFlexVolume(volume: FlexVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "FlexVolume (a generic volume resource that is provisioned/attached using an exec based plugin)")
			.add("Driver", volume.driver)
			.add("FSType", volume.fsType)
			.add("SecretRef", volume.secretRef?.name)
			.add("ReadOnly", volume.readOnly)
			.addSequence("Options", volume.options?.map { option ->
				"${option.key} -> ${option.value}"
			})
	}

	private fun addFlocker(flocker: FlockerVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Flocker (a Flocker volume mounted by the Flocker agent)")
			.add("DatasetName", flocker.datasetName)
			.add("DatasetUUID", flocker.datasetUUID)
	}

	private fun addProjected(projected: ProjectedVolumeSource, parent: Chapter) {
		parent.addIfExists(TITLE_TYPE, "Projected (a volume that contains injected data from multiple sources)")
		projected.sources.forEach { source ->
			when {
				source.secret != null -> {
					parent.add("SecretName", source.secret.name)
					parent.add("SecretOptionalName", source.secret.optional)
				}
				source.downwardAPI != null -> {
					parent.add("DownwardAPI", true)
				}
				source.configMap != null -> {
					parent.add("ConfigMapName", source.configMap.name)
					parent.add("ConfigMapOptional", source.configMap.optional)
				}
				source.serviceAccountToken != null -> {
					parent.add("TokenExpirationSeconds", source.serviceAccountToken.expirationSeconds)
				}
			}
		}
	}

}