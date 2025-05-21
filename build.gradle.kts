import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.*
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats.*

plugins {
    java // Java support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.kotlinJvm)
    id("idea")
}

group = "com.redhat.devtools.intellij"
version = providers.gradleProperty("projectVersion").get() // Plugin version
val ideaVersion = providers.gradleProperty("platformVersion").get()
val javaVersion = 17
val ideaVersionInt = when {
    // e.g. '20XY.Z'
    ideaVersion.length == 6 -> ideaVersion.replace(".", "").substring(2).toInt()
    // e.g. '2XY.ABCDE.12'
    else -> ideaVersion.substringBefore(".").toInt()
}

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
    maven { url = uri("https://raw.githubusercontent.com/redhat-developer/intellij-common-ui-test-library/repository/snapshots") }
    maven { url = uri("https://raw.githubusercontent.com/redhat-developer/intellij-common-ui-test-library/repository/releases") }
    maven { url = uri("https://raw.githubusercontent.com/redhat-developer/intellij-common/repository/snapshots") }
    maven { url = uri("https://raw.githubusercontent.com/redhat-developer/intellij-common/repository/releases") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(ideaVersion)

        // Bundled Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        // starting from 2024.3, all json related code is know on its own plugin
        val platformBundledPlugins =  ArrayList<String>()
        platformBundledPlugins.addAll(providers.gradleProperty("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.get())
        /*
         * platformVersion check for JSON breaking changes since 2024.3
         */
        if (ideaVersionInt >= 243) {
            platformBundledPlugins.add("com.intellij.modules.json")
        }
        println("use bundled Plugins: $platformBundledPlugins")
        bundledPlugins(platformBundledPlugins)

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // for local plugin -> https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-add-a-dependency-on-a-plugin-available-in-the-file-system
        //plugins.set(listOf(file("/path/to/plugin/")))

        pluginVerifier()

        testFramework(TestFrameworkType.Platform)
    }

    implementation(libs.devtools.common)
    implementation(libs.openshift.client)
    implementation(libs.kubernetes.client)
    implementation(libs.kubernetes.model)
    implementation(libs.kubernetes.model.common)
    implementation(libs.kubernetes.httpclient.okhttp)
    implementation(libs.jackson.core)
    implementation(libs.commons.lang3)

    // for unit tests
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito)
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

configurations {
    runtimeClasspath {
        exclude(group = "org.slf4j", module = "slf4j-api")
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
        failureLevel = listOf(INVALID_PLUGIN, COMPATIBILITY_PROBLEMS, MISSING_DEPENDENCIES)
        verificationReportsFormats = listOf(MARKDOWN, PLAIN)
        ides {
            recommended()
        }
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId,TemplateWordInPluginName"
        )
    }
}

tasks {
    fun supportsEnhancedClassRedefinition(): Boolean {
        return ideaVersionInt >= 241
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    runIde {
        if (supportsEnhancedClassRedefinition()) {
            jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=fatjar")
        }
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

sourceSets {
    create("it") {
        description = "integrationTest"
        java.srcDir("src/it/java")
        resources.srcDir("src/it/resources")
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.test.get().compileClasspath
        runtimeClasspath += output + compileClasspath + sourceSets.test.get().runtimeClasspath
    }
}

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
