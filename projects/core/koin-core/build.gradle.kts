import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("maven-publish")
}

group = "com.boyasec.kmp"
version = "4.0.1"
val artifact by extra("koin-core")

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            groupId = project.group.toString()
            version = project.version.toString()
            artifactId = artifact
        }
    }

    repositories {
        maven {
            credentials {
                username = "admin"
                password = "juj88P#FPng3"
            }
            val releasesRepoUrl = "http://192.168.50.32:8081/repository/maven-releases/"
            val snapshotsRepoUrl = "http://192.168.50.32:8081/repository/maven-snapshots/"
            url = uri(if ((version as String).endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            isAllowInsecureProtocol = true
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }

    jvm {
        withJava()
    }

    js(IR) {
        nodejs()
        browser()
        binaries.executable()
    }

    wasmJs {
        nodejs()
        binaries.executable()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    /*watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    watchosX64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()*/
    mingwX64()
    linuxX64()
    linuxArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.extras.stately)
            implementation(libs.extras.stately.collections)
//            api(libs.extras.uuid)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
        }
    }
}


tasks.withType<KotlinCompile>().all {
    compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
}

