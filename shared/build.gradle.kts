import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.multiplatform.swiftpackage)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kmmbridge)
    id("maven-publish")
}

group = libs.versions.library.group.get()
version = libs.versions.library.version.get()
val moduleName = "FitJournalKMP"

kotlin {
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
        @Suppress("OPT_IN_USAGE")
        targetHierarchy.default()

        val commonMain by getting {
            dependencies {
                api(libs.kotlin.stdlib)
            }
        }

        // Android
        val androidMain by getting {
            dependencies {
                //Add your specific android dependencies here
            }
        }

        // iOS
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                //Add any ios specific dependencies here, remember to also add them to the export block
            }
        }
    }
}

android {
    namespace = libs.versions.library.group.get()
    compileSdk = libs.versions.compileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSDK.get().toInt()
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    dependencies {
        coreLibraryDesugaring(libs.android.tools.desugaring)
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
