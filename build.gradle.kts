import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.kotlinJvm)
    id("idea")
}

group = "com.redhat.devtools.intellij"
version = providers.gradleProperty("projectVersion").get() // Plugin version
val ideaVersion = providers.gradleProperty("platformVersion").get()
val javaVersion = 17

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(javaVersion)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}


repositories {
    mavenLocal()
    maven { url = uri("https://repository.jboss.org") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaCommunity, ideaVersion)

        // Bundled Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // for local plugin -> https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-add-a-dependency-on-a-plugin-available-in-the-file-system
        //plugins.set(listOf(file("/path/to/plugin/")))

        pluginVerifier()

        instrumentationTools()

        testFramework(TestFrameworkType.Platform)
    }

    implementation(libs.devtools.common)
    implementation(libs.kubernetes.client)
    implementation(libs.kubernetes.model)
    implementation(libs.kubernetes.model.common)
    implementation(libs.openshift.client)
    implementation(libs.kubernetes.httpclient.okhttp)
    implementation(libs.jackson.core)
    implementation(libs.commons.lang3)

    // for unit tests
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test.junit)

    components {
        withModule("com.redhat.devtools.intellij:intellij-common") {
            withVariant("intellijPlatformComposedJar") {
                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
                }
            }
        }
    }

}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    publishing {
        token = providers.gradleProperty("jetBrainsToken")
        channels = providers.gradleProperty("jetBrainsChannel").map { listOf(it) }
    }

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaUltimate, ideaVersion)
        }
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId,TemplateWordInPluginName"
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    runIde {
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "debug")
    }

    test {
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "disabled")
    }

    printProductsReleases {
        channels = listOf(ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        untilBuild = provider { null }
    }
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-api")
}

sourceSets {
    create("it") {
        description = "integrationTest"
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.test.get().compileClasspath
        runtimeClasspath += output + compileClasspath
    }
}

configurations["itRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())
configurations["itImplementation"].extendsFrom(configurations.testImplementation.get())

val integrationTest by intellijPlatformTesting.testIde.registering {
    task {
        useJUnitPlatform()
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "disabled")
        description = "Runs the integration tests."
        group = "verification"
        testClassesDirs = sourceSets["it"].output.classesDirs
        classpath = sourceSets["it"].runtimeClasspath
        jvmArgs("-Djava.awt.headless=true")
        shouldRunAfter(tasks["test"])
    }

    plugins {
        robotServerPlugin()
    }

    dependencies {
        testImplementation(libs.devtools.common)
        testImplementation(libs.devtools.common.ui.test)
        testImplementation(libs.junit.platform.launcher)
        testImplementation(libs.junit.platform.suite)
        testImplementation(libs.junit.jupiter)
        testImplementation(libs.junit.jupiter.api)
        testImplementation(libs.junit.jupiter.engine)
        testImplementation(libs.gson)
    }
}


// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#runIdeForUiTests
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Duser.language=en -Duser.country=US"
            )
        }

        systemProperty("robot-server.port", System.getProperty("robot-server.port"))
        systemProperties["com.redhat.devtools.intellij.telemetry.mode"] = "disabled"
    }

    plugins {
        robotServerPlugin()
    }
}

// below is only to correctly configure IDEA project settings
idea {
    module {
        testSources.from(sourceSets["it"].java.srcDirs)
    }
}