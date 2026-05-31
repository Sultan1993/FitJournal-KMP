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

    // Test-only JVM target. Lets the pure-commonMain repositories be unit-tested
    // with an in-memory JDBC SQLite driver (see src/jvmTest). The Android and iOS
    // variants the apps actually consume are unaffected — nothing depends on this
    // target. See docs/sync-migration-architecture.md §10.
    jvm()

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
            // Match the iOS app's `IPHONEOS_DEPLOYMENT_TARGET = 17.0`. Kotlin
            // 2.3.20 defaults K/N's iOS deployment target to 14.0; Xcode 26.4
            // / Swift 6.3.1 rejects the resulting `.swiftmodule` as "built
            // for incompatible target" and falls back to a partial
            // swiftinterface compile, dropping symbols. Pinning to 17 here
            // realigns it.
            freeCompilerArgs += listOf(
                "-Xoverride-konan-properties=osVersionMin.ios_arm64=17.0;osVersionMin.ios_simulator_arm64=17.0",
            )
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

        // JVM (test harness only)
        val jvmMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

sqldelight {
    databases {
        create("FitJournalDatabase") {
            packageName = "kz.maestrosultan.fitjournal.data.db"
            // NOTE: verifyMigrations is intentionally NOT enabled. SQLDelight's
            // migration verifier replays .sqm files from an empty database, but
            // the released FJ1.x migrations 1–7 ALTER tables that were created by
            // Schema.create() (the .sq of that era), not by a migration — so
            // `1.sqm`'s `ALTER TABLE notes RENAME COLUMN back4AppId …` can't be
            // resolved against an empty base ("no table found with name notes").
            // Enabling it would require rewriting frozen released migrations.
            // The squashed 8.sqm is instead verified out-of-band (it drops the v8
            // tables and recreates the .sq schema verbatim; replaying it onto a v8
            // snapshot reproduces a fresh install — proven via sqlite3 and
            // SQLDelight's own verifyCommonMainFitJournalDatabaseMigration task).
        }
    }
}

skie {
    // Disable phone-home analytics (upload task can hang on slow networks).
    analytics {
        disableUpload.set(true)
    }
}
