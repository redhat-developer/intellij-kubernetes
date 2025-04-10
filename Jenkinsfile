#!/usr/bin/env groovy

node('rhel7'){
	def recipientList = 'jbosstools-builds@lists.jboss.org'
	def javaHome = tool 'openjdk-17'
	env.JAVA_HOME = "${javaHome}"

	try {
		stage('Checkout repo') {
			deleteDir()
			git url: 'https://github.com/redhat-developer/intellij-kubernetes',
				branch: "${sha1}"
		}
	
		def props = readProperties file: 'gradle.properties'
		def isSnapshot = props['projectVersion'].contains('-SNAPSHOT')
		def version = isSnapshot?props['projectVersion'].replace('-SNAPSHOT', ".${env.BUILD_NUMBER}"):props['projectVersion'] + ".${env.BUILD_NUMBER}"
	
		stage('Build') {
			sh "./gradlew assemble  -PprojectVersion=${version}"
		}
	
		stage('Package') {
			sh "./gradlew buildPlugin -PprojectVersion=${version}"
		}
	
		if(params.UPLOAD_LOCATION) {
			stage('Upload') {
				def filesToPush = findFiles(glob: '**/*.zip')
				sh "sftp -C ${UPLOAD_LOCATION}/snapshots/intellij-kubernetes/ <<< \$'put -p \"${filesToPush[0].path}\"'"
				stash name:'zip', includes:filesToPush[0].path
			}
		}
	
		if(publishToMarketPlace.equals('true')){
			timeout(time:5, unit:'DAYS') {
				input message:'Approve deployment?', submitter: 'jmaury,adietish'
			}
	
			def channel = isSnapshot?"nightly":"stable"
	
			stage("Publish to Marketplace") {
				unstash 'zip'
				withCredentials([[$class: 'StringBinding', credentialsId: 'JetBrains marketplace token', variable: 'TOKEN']]) {
					sh "./gradlew publishPlugin -PjetBrainsToken=${TOKEN} -PprojectVersion=${version} -PjetBrainsChannel=${channel}"
				}
				archive includes:"**.zip"
	
				if (!isSnapshot) {
					stage("Promote the build to stable") {
						def zip = findFiles(glob: '**/*.zip')
						sh "sftp -C ${UPLOAD_LOCATION}/stable/intellij-kubernetes/ <<< \$'put -p \"${zip[0].path}\"'"
						currentBuild.keepLog = true
						currentBuild.description = "${version}"
					}
				}
			}
		}
	} catch (any) {
		currentBuild.result = 'FAILURE'
		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${recipientList}", sendToIndividuals: true])
		throw any
	}
}
