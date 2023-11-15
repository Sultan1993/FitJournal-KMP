import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.multiplatform.swiftpackage)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    id("maven-publish")
}

group = "kz.maestrosultan.fitjournal.kmp"
version = "0.2.4"
val moduleName = "FitJournalKMP"

kotlin {
    applyDefaultHierarchyTemplate()

    // Android
    androidTarget {
        publishLibraryVariants("release")
    }

    // iOS
    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = moduleName
            export(libs.kotlin.stdlib)
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Android
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android)
            }
        }

        // iOS
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependencies {
                implementation(libs.sqldelight.native)
            }
        }
    }
}

android {
    namespace = group.toString()
    compileSdk = libs.versions.compileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSDK.get().toInt()
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    repositories {
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/Sultan1993/FitJournal-KMP")
            credentials {
                username = "Sultan1993"
                password = "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"
            }
        }
    }

    publications {
        // Publish only main (Android)
        matching { it.name == "kotlinMultiplatform" }.all {
            val targetPublication = this@all
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .configureEach { onlyIf { findProperty("isMainHost") == "true" } }
        }

        // Disable publishing iOS version to Maven
        matching { it.name.contains("ios", true) }.all {
            val targetPublication = this@all
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .forEach { it.enabled = false }
        }
    }
}

multiplatformSwiftPackage {
    swiftToolsVersion("5.8")
    packageName(moduleName)
    zipFileName(moduleName)
    outputDirectory(File(rootDir, "swiftpackage/FitJournal-SPM"))
    targetPlatforms {
        iOS { v("14") }
    }
}

sqldelight {
    databases {
        create("FitJournalDatabase") {
            packageName.set("kz.maestrosultan.fitjournal.kmp")
        }
    }
}

// An alias for "publish" function that publishes Android code. Just for convenience
val publishAndroid by tasks.registering {
    dependsOn(tasks.named("publish"))
}

// An alias for "createSwiftPackage" function that publishes iOS code. Just for convenience
val publishIos by tasks.registering {
    dependsOn(tasks.named("createSwiftPackage"))
}

val publishPlatforms by tasks.registering {
    dependsOn(
        tasks.named("generateSqlDelightInterface"),
        tasks.named("publishAndroid"),
        tasks.named("publishIos")
    )
}
