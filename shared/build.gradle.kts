import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.skie)
}

group = "kz.maestrosultan.fitjournal.kmp"
// FJ 2.0 baseline. See docs/fj-2.0-migration-plan.md for the consolidation
// plan moving domain entities + repositories + use cases into shared.
version = "0.5.0"

val moduleName = "FitJournalKMP"

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(17)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    androidTarget()

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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sqldelight.coroutines)
                // SKIE handles Swift interop annotations automatically; no
                // explicit annotation library needed in commonMain.
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
}

sqldelight {
    databases {
        create("FitJournalDatabase") {
            packageName = "kz.maestrosultan.fitjournal.kmp"
        }
    }
}
