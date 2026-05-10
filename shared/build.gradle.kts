import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.skie)
}

group = "kz.maestrosultan.fitjournal"
version = "0.5.0"

val moduleName = "FitJournalKMP"

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    // KMP plugin handles Android config; no separate `android { }` block.
    androidLibrary {
        namespace = "kz.maestrosultan.fitjournal.multiplatform"
        compileSdk = libs.versions.compileSDK.get().toInt()
        minSdk = libs.versions.minSDK.get().toInt()
    }

    // iOS
    val xcf = XCFramework()
    listOf(
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
                // SKIE handles Swift interop; no explicit annotations needed.
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
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependencies {
                implementation(libs.sqldelight.native)
            }
        }
    }
}

sqldelight {
    databases {
        create("FitJournalDatabase") {
            packageName = "kz.maestrosultan.fitjournal.data.db"
        }
    }
}

skie {
    // Disable phone-home analytics (upload task can hang on slow networks).
    analytics {
        disableUpload.set(true)
    }
}
