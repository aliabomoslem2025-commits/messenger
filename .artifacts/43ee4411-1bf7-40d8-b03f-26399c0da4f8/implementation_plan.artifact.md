# Complete Project Build Fix

This plan aims to resolve all remaining compilation errors by merging the root source tree, fixing design system references, updating the JVM target, and correcting SDK usage patterns.

## User Review Required

> [!IMPORTANT]
> - This plan involves moving and overwriting many source files. I will use the root `com/` folder as the primary source of truth for SDK usage, as it appears more complete and correct than the `app/` folder.
> - JVM Target will be updated to **Java 21** to resolve library compatibility issues.

## Proposed Changes

### Core & Infrastructure

#### [MODIFY] [build.gradle.kts (app)](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/build.gradle.kts)
- Set `jvmTarget` to "21".
- Set `sourceCompatibility` and `targetCompatibility` to `JavaVersion.VERSION_21`.

#### [MODIFY] [MatrixModule.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/di/MatrixModule.kt)
- Update initialization to use `Matrix.getInstance(context)` if available, or the correct object pattern.
- Fix `MatrixConfiguration` parameters to match SDK version 1.6.36.

#### [MODIFY] [AppDatabase.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/local/database/AppDatabase.kt)
- Remove `@Inject` from the abstract class constructor.

### Design System

#### [NEW] [MatrixColors.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/designsystem/MatrixColors.kt)
- Relocate and fix the color definitions.

#### [NEW] [MatrixTypography.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/designsystem/MatrixTypography.kt)
- Relocate and fix the typography definitions (fix static property access).

#### [NEW] [MatrixDimens.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/designsystem/MatrixDimens.kt)
- Create missing dimension tokens.

### Data & Domain Merge

#### [MODIFY] [Source Merge]
- Move files from root `com/matrixmessenger/` to `app/src/main/java/com/matrixmessenger/`.
- Ensure `SessionPreferences` and `AppPreferences` are correctly unified.

### Code Quality & Syntax

#### [MODIFY] [AnimationEngine.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/animation/engine/AnimationEngine.kt)
- Fix coroutine syntax and missing imports.

#### [MODIFY] [SearchViewModel.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/search/presentation/viewModel/SearchViewModel.kt)
- Fix `Channel` and `launch` unresolved references.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify compilation.
- Verify Dagger/Hilt graph generation via `./gradlew kspDebugKotlin`.

### Manual Verification
- Launch the app and ensure the login screen appears with correct styling.
- Verify navigation to home screen (if credentials available).
