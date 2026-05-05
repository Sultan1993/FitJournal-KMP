# FitJournalKMP

Kotlin Multiplatform shared module for the Fit Journal Android and iOS apps.
Holds the SQLDelight schema (single source of truth for the on-device database)
and any code that needs to be shared between platforms.

## Repository layout requirement

Both apps consume this module via **relative path** (`../Multiplatform`), so all
three repos must be checked out as siblings:

```
~/some/parent/dir/
├── iOS/                 # FitJournal iOS app
├── Android/             # FitJournal Android app
└── Multiplatform/       # ← this repo
```

If `Multiplatform/` is missing or in a different location, neither app can
build. There is no Maven publish or SPM checkout fallback in the apps' current
configuration.

## How the apps consume this module

### Android — Gradle composite build

`Android/settings.gradle.kts` includes this build directly:

```kotlin
includeBuild("../Multiplatform") {
    dependencySubstitution {
        substitute(module("kz.maestrosultan.fitjournal.kmp:shared-android"))
            .using(project(":shared"))
    }
}
```

Android modules keep their existing Maven-style dependency
(`api(libs.fitjournal.kmp)`); Gradle silently substitutes the local
`:shared` project at resolution time. KMP source changes are picked up on
the next Android build, no publish step.

### iOS — Xcode Run Script + Gradle

`FitJournal.xcodeproj` has a `Build KMP framework` Run Script build phase
(at index 0, before Compile Sources) that invokes:

```bash
cd "$SRCROOT/../Multiplatform"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

The Gradle task picks the right framework slice from Xcode's `CONFIGURATION` /
`SDK_NAME` / `ARCHS` env vars, builds it, signs it, embeds it into the app
bundle. The iOS project links against it via `OTHER_LDFLAGS = -framework
FitJournalKMP` plus `FRAMEWORK_SEARCH_PATHS` pointing at `$BUILT_PRODUCTS_DIR/$FRAMEWORKS_FOLDER_PATH`
(build-time) and `$SRCROOT/../Multiplatform/shared/build/xcode-frameworks/$CONFIGURATION/$SDK_NAME`
(IDE-time).

iOS also requires a JDK on the build machine. See
`iOS/../docs/ios-kmp-build-phase-migration.md` for the migration history and
troubleshooting notes.

## Local development

Make a change in `shared/src/commonMain/kotlin/...` or in a `.sq` file under
`shared/src/commonMain/sqldelight/...`, then build either app — the change
is compiled into the framework / Android library on the next build. No
explicit publish.

If the iOS IDE's autocomplete for `import FitJournalKMP` ever stops
working (e.g. after `./gradlew clean`), repopulate the indexed copy:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 \
          :shared:linkDebugFrameworkIosArm64 \
          :shared:linkReleaseFrameworkIosArm64
mkdir -p shared/build/xcode-frameworks/Debug/{iphonesimulator,iphoneos} \
         shared/build/xcode-frameworks/Release/iphoneos
cp -R shared/build/bin/iosSimulatorArm64/debugFramework/FitJournalKMP.framework \
      shared/build/xcode-frameworks/Debug/iphonesimulator/
cp -R shared/build/bin/iosArm64/debugFramework/FitJournalKMP.framework \
      shared/build/xcode-frameworks/Debug/iphoneos/
cp -R shared/build/bin/iosArm64/releaseFramework/FitJournalKMP.framework \
      shared/build/xcode-frameworks/Release/iphoneos/
```

After the next interactive Xcode build, `$BUILT_PRODUCTS_DIR` is also
populated and IDE features work from either path.

## Tasks worth knowing

- `:shared:assemble` — build all targets
- `:shared:embedAndSignAppleFrameworkForXcode` — what the iOS Run Script invokes
- `:shared:linkDebugFrameworkIos<target>` — emit a single Debug framework slice
- `:shared:linkReleaseFrameworkIos<target>` — emit a single Release framework slice

The `multiplatformSwiftPackage` plugin and its `createSwiftPackage` task are
still configured but unused by the iOS app. Kept in case someone needs to
publish a standalone Swift Package later.

## Targets

- `androidTarget` — published as `kz.maestrosultan.fitjournal.kmp:shared-android`
- `iosArm64`, `iosSimulatorArm64`, `iosX64` — combined into the `FitJournalKMP` XCFramework
