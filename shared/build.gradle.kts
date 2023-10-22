import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.multiplatform.swiftpackage)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    id("maven-publish")
}

group = libs.versions.library.group.get()
version = libs.versions.library.version.get()

val moduleName = "FitJournalKMP"
var androidTarget: String = ""

kotlin {
    val android = androidTarget {
        publishLibraryVariants("release")
    }
    androidTarget = android.name

    // iOS
    val xcf = XCFramework()
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSim = iosSimulatorArm64()
    configure(listOf(iosX64, iosArm64, iosSim)) {
        binaries {
            framework {
                //Any dependecy you add for ios should be added here using export()
                baseName = moduleName
                export(libs.kotlin.stdlib)
                xcf.add(this)
            }
        }
    }

    targets.withType<KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += listOf("-Xgc=cms")
        }
    }

    sourceSets {
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
        val iosMain by creating {
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
    compileSdk = libs.versions.compile.sdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    dependencies {
        coreLibraryDesugaring(libs.android.tools.desugaring)
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
            url = uri("https://maven.pkg.github.com/user/repo")
            credentials {
                username = System.getenv()["MYUSER"]
                password = System.getenv()["MYPAT"]
            }
        }
    }
    val androidPublications = listOf(androidTarget) + "kotlinMultiplatform"
    publications {
        matching { it.name in androidPublications }.all {
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

configurations.forEach {
    it.attributes {
        attribute(Attribute.of("buildIdAttribute", String::class.java), it.name)
    }
}

val publishPlatforms by tasks.registering {
    group = libs.versions.library.group.get()
    dependsOn(
        tasks.named("publishAndroidReleasePublicationToGithubRepository"),
    )
    doLast {
        exec { commandLine = listOf("git", "add", "-A") }
        exec { commandLine = listOf("git", "commit", "-m", "iOS binary lib for version ${libs.versions.library.version.get()}") }
        exec { commandLine = listOf("git", "push", "origin", "main") }
        exec { commandLine = listOf("git", "tag", libs.versions.library.version.get()) }
        exec { commandLine = listOf("git", "push", "--tags") }
        println("version ${libs.versions.library.version.get()} built and published")
    }
}

val compilePlatforms by tasks.registering {
    group = libs.versions.library.group.get()
    dependsOn(
        tasks.named("compileKotlinIosArm64"),
        tasks.named("compileKotlinIosX64"),
        tasks.named("compileKotlinIosSimulatorArm64"),
        tasks.named("compileReleaseKotlinAndroid")
    )
    doLast {
        println("Finished compilation")
    }
}

multiplatformSwiftPackage {
    swiftToolsVersion("5.8")
    packageName(moduleName)
    zipFileName(moduleName)
    targetPlatforms {
        iOS { v("14") }
    }
}
