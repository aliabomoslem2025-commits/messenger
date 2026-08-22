# Fix Kotlin Plugin Resolution Error

The project fails to build because the Kotlin Android plugin cannot be resolved. This is often due to the "marker artifact" not being found in the configured repositories, especially when using mirrors or in restricted network environments.

## User Review Required

> [!IMPORTANT]
> I am adding a `buildscript` block to the root `build.gradle.kts`. This is a slightly older but more robust way to ensure plugins are available on the classpath when the modern `plugins` block resolution fails.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/gradle/libs.versions.toml)
- Add `kotlin-gradle-plugin` to the `[libraries]` section so it can be referenced in the build script.

#### [MODIFY] [settings.gradle.kts](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/settings.gradle.kts)
- Ensure repositories in `pluginManagement` are correctly configured and ordered to prioritize available mirrors and standard repos.

#### [MODIFY] [build.gradle.kts (root)](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/build.gradle.kts)
- Add a `buildscript` block to include the Kotlin Gradle plugin on the classpath.

## Verification Plan

### Automated Tests
- Run `./gradlew help` to check plugin resolution.
- Run `./gradlew :app:assembleDebug` to verify the build completes.
