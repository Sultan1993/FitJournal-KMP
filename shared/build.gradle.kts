import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.skie)
    // Compose Multiplatform: the shared module now also carries UI (the Workout
    // screen, first of many). Compiler comes from kotlin.plugin.compose (Kotlin
    // 2.3.21); the `compose` plugin brings the runtime + `compose.*` DSL.
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
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

    // KMP Android library target (AGP 9.3 renamed `androidLibrary` → `android`).
    android {
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
            // MUST equal the iOS app's `IPHONEOS_DEPLOYMENT_TARGET` (currently
            // 18.0 — iOS 17 dropped 2026-07). Kotlin 2.3.20 defaults K/N's iOS
            // deployment target to 14.0; when the framework's swiftmodule target
            // doesn't match the app's, Swift (Xcode 26.4+/6.3.x) rejects it as
            // "built for incompatible target" and falls back to a partial
            // swiftinterface compile, dropping every SKIE-bridged symbol. Bump
            // this in lockstep whenever the app's deployment target changes.
            freeCompilerArgs += listOf(
                "-Xoverride-konan-properties=osVersionMin.ios_arm64=18.0;osVersionMin.ios_simulator_arm64=18.0",
            )
        }
    }

    // Modern KMP source-set accessor DSL (the `val x by getting` delegate form
    // is deprecated as of Gradle 9.6 — incompatible with Gradle 10).
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.coroutines)
            // SKIE handles Swift interop; no explicit annotations needed.

            // Compose Multiplatform UI (kept in commonMain per the "one huge
            // module" directive — clean layering enforced by package discipline:
            // domain/ and data/ must NEVER import androidx.compose.*).
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.core)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.calendar.compose)
            implementation(libs.reorderable)
            implementation(libs.vico.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // v2 runComposeUiTest API; call sites need @OptIn(ExperimentalTestApi::class).
            implementation(libs.compose.ui.test)
        }

        // Android
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
            // @Preview annotation + Android Studio's preview renderer, androidMain-only:
            // compose.components.uiToolingPreview in commonMain breaks
            // compileCommonMainKotlinMetadata (no compatible variant for this module's
            // bare jvm() test target), so the preview files live in androidMain instead
            // (see ui/workoutlist/preview/). com.android.kotlin.multiplatform.library
            // (this module's android target) also has no debug/release variants — only
            // one "androidMain" — so there's no debugImplementation config to scope
            // ui-tooling to; it ships in the single variant like every other dependency
            // here.
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.ui.tooling)
        }

        // iOS — iosArm64Main / iosSimulatorArm64Main come from the default
        // hierarchy template; the shared iosMain intermediate carries the deps.
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }

        // JVM (test harness only)
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.desktop.currentOs)
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

// Compose Multiplatform resources: defaults are fine. Files live in
// shared/src/commonMain/composeResources/{font,drawable,values}. The generated
// accessor is `<default-package>.generated.resources.Res`.
