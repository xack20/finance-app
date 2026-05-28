# Hisaab Phase P0a — Project Scaffold + Design System + Empty UI Shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Kotlin Multiplatform + Compose Multiplatform project that builds and opens on Android, iOS, and Web (wasmJs), showing a single editorial-styled empty "Today" screen, with the design system tokens in place.

**Architecture:** Single `composeApp` Gradle module with `commonMain` plus platform source sets (`androidMain`, `iosMain`, `wasmJsMain`). Separate `iosApp/` Xcode project hosts the Compose UIViewController. Design tokens (Colors, Typography, Spacing, Shapes) live in `commonMain` and feed a `HisaabTheme` wrapper around Material3.

**Tech Stack:**
- Kotlin **2.3.20** (or latest stable per https://kotlinlang.org/docs/releases.html — verify at execution time)
- Compose Multiplatform **1.10.1** (or latest stable per https://github.com/JetBrains/compose-multiplatform/releases — verify at execution time)
- Android Gradle Plugin **8.7+**
- Gradle **8.10+**
- Min SDK 26 (Android 8.0); Target SDK 35
- iOS Deployment Target 17.0
- kotlin.test for common tests

**Companion spec:** [`../specs/2026-05-27-hisaab-master-architecture-design.md`](../specs/2026-05-27-hisaab-master-architecture-design.md)
**Companion product doc:** [`../../idea.md`](../../idea.md)

**Out of scope for this plan (P0b and later):**
- Local DB (SQLDelight + SQLCipher) — P0b
- Supabase phone OTP signup — P0b
- Real fonts (GT Sectra, Inter, Noto Serif Bengali, Hind Siliguri) — bundled in P0b once licensing resolved; v1 uses system serif/sans
- Bengali numerals — P0b after locale infrastructure
- Voice / SMS / Gmail / advisor — Phase P1+

---

## File structure (locked-in decomposition)

```
finance-app/
├── .editorconfig                       (Task 1)
├── gradle.properties                   (Task 4)
├── settings.gradle.kts                 (Task 4)
├── build.gradle.kts                    (Task 4)
├── gradle/
│   ├── libs.versions.toml              (Task 3)
│   └── wrapper/                        (Task 2)
├── gradlew, gradlew.bat                (Task 2)
│
├── composeApp/
│   ├── build.gradle.kts                (Task 5)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/app/hisaab/
│       │   │   ├── PlatformInfo.kt            (Task 7 — expect)
│       │   │   ├── App.kt                     (Task 18)
│       │   │   ├── design/
│       │   │   │   ├── HisaabColors.kt        (Task 11)
│       │   │   │   ├── HisaabTypography.kt    (Task 12)
│       │   │   │   ├── HisaabSpacing.kt       (Task 13)
│       │   │   │   ├── HisaabShapes.kt        (Task 14)
│       │   │   │   └── HisaabTheme.kt         (Task 15)
│       │   │   └── screens/today/
│       │   │       └── TodayScreen.kt         (Task 16)
│       │   └── composeResources/
│       │       └── (font files in P0b)
│       ├── commonTest/kotlin/app/hisaab/
│       │   ├── PlatformInfoTest.kt            (Task 7)
│       │   └── design/
│       │       ├── HisaabColorsTest.kt        (Task 11)
│       │       ├── HisaabTypographyTest.kt    (Task 12)
│       │       └── HisaabSpacingTest.kt       (Task 13)
│       ├── androidMain/
│       │   ├── kotlin/app/hisaab/
│       │   │   ├── PlatformInfo.android.kt    (Task 8)
│       │   │   ├── HisaabApplication.kt       (Task 19)
│       │   │   └── MainActivity.kt            (Task 21)
│       │   └── AndroidManifest.xml            (Task 20)
│       ├── iosMain/kotlin/app/hisaab/
│       │   ├── PlatformInfo.ios.kt            (Task 9)
│       │   └── MainViewController.kt          (Task 24)
│       └── wasmJsMain/
│           ├── kotlin/app/hisaab/
│           │   ├── PlatformInfo.wasmJs.kt     (Task 10)
│           │   └── main.kt                    (Task 27)
│           └── resources/index.html           (Task 28)
│
└── iosApp/                                     (Task 23)
    ├── Configuration/Config.xcconfig
    ├── iosApp.xcodeproj/project.pbxproj
    └── iosApp/
        ├── iOSApp.swift                       (Task 25)
        ├── ContentView.swift                  (Task 25)
        ├── Info.plist                         (Task 23)
        └── Assets.xcassets/AppIcon.appiconset/Contents.json
```

---

## Section A — Project Initialization

### Task 1: Add `.editorconfig`

**Files:**
- Create: `.editorconfig`

- [ ] **Step 1: Create the file**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true
max_line_length = 120

[*.{kt,kts}]
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

[*.{yml,yaml,json,toml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
max_line_length = off
```

- [ ] **Step 2: Commit**

```bash
git add .editorconfig
git commit -m "chore: add editorconfig for cross-IDE formatting"
```

---

### Task 2: Add Gradle wrapper

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`

- [ ] **Step 1: Initialize a temporary Gradle wrapper using a host Gradle install**

Run from the project root:

```bash
gradle wrapper --gradle-version 8.10 --distribution-type bin
```

If no `gradle` binary is on `PATH`, install via SDKMAN: `sdk install gradle 8.10`, then re-run.

Expected: creates `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`.

- [ ] **Step 2: Verify wrapper resolves Gradle**

```bash
./gradlew --version
```

Expected: prints `Gradle 8.10` (or matching version) and JVM info. Does NOT fail.

- [ ] **Step 3: Commit**

```bash
git add gradle/wrapper/ gradlew gradlew.bat
git commit -m "chore: add gradle wrapper 8.10"
```

---

### Task 3: Create version catalog `gradle/libs.versions.toml`

**Files:**
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Write the catalog**

```toml
[versions]
kotlin = "2.3.20"
agp = "8.7.2"
compose-multiplatform = "1.10.1"
androidx-activity-compose = "1.10.0"
androidx-core-ktx = "1.15.0"
androidx-lifecycle = "2.8.7"
kotlinx-coroutines = "1.10.1"
junit = "4.13.2"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity-compose" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core-ktx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

> Verify all versions exist at https://central.sonatype.com/ and https://github.com/JetBrains/compose-multiplatform/releases before continuing. Bump to latest stable if newer versions exist.

- [ ] **Step 2: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add Gradle version catalog"
```

---

### Task 4: Create root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`

- [ ] **Step 1: Write `settings.gradle.kts`**

```kotlin
rootProject.name = "hisaab"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
```

- [ ] **Step 2: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx4g -XX:+UseParallelGC

org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

android.useAndroidX=true
android.nonTransitiveRClass=true

org.jetbrains.compose.experimental.uikit.enabled=true
org.jetbrains.compose.experimental.wasm.enabled=true
```

- [ ] **Step 4: Verify build configuration**

```bash
./gradlew help
```

Expected: succeeds without errors. Confirms catalog references and plugin declarations are valid.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties
git commit -m "chore: add root gradle build files with plugin DSL"
```

---

## Section B — composeApp module setup

### Task 5: Create `composeApp/build.gradle.kts`

**Files:**
- Create: `composeApp/build.gradle.kts`

- [ ] **Step 1: Write the build file**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "app.hisaab"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.hisaab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-p0a"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.hisaab.resources"
    generateResClass = auto
}
```

- [ ] **Step 2: Create source set directories**

```bash
mkdir -p composeApp/src/commonMain/kotlin/app/hisaab/design
mkdir -p composeApp/src/commonMain/kotlin/app/hisaab/screens/today
mkdir -p composeApp/src/commonMain/composeResources
mkdir -p composeApp/src/commonTest/kotlin/app/hisaab/design
mkdir -p composeApp/src/androidMain/kotlin/app/hisaab
mkdir -p composeApp/src/androidMain/res
mkdir -p composeApp/src/iosMain/kotlin/app/hisaab
mkdir -p composeApp/src/wasmJsMain/kotlin/app/hisaab
mkdir -p composeApp/src/wasmJsMain/resources
```

- [ ] **Step 3: Verify Gradle resolves the module**

```bash
./gradlew :composeApp:dependencies --configuration commonMainCompileClasspath | head -30
```

Expected: resolves Compose runtime/foundation/material3/ui dependencies without errors.

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/
git commit -m "feat(composeApp): add module build with Android, iOS, wasmJs targets"
```

---

### Task 6: Verify the empty composeApp builds (Android target)

**Files:** none new — verification only

- [ ] **Step 1: Run assembleDebug for Android**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: fails with `AndroidManifest.xml not found` — that file arrives in Task 20. This task verifies the Gradle configuration is otherwise valid.

If the failure is something other than missing manifest (e.g., plugin resolution, version conflict, JVM target mismatch), fix the build file before continuing.

- [ ] **Step 2: No commit (verification only)**

---

### Task 7: `PlatformInfo` expect declaration with TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/PlatformInfo.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/PlatformInfoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// composeApp/src/commonTest/kotlin/app/hisaab/PlatformInfoTest.kt
package app.hisaab

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformInfoTest {
    @Test
    fun platformName_isNotBlank() {
        val info = PlatformInfo()
        assertNotNull(info.name)
        assertTrue(info.name.isNotBlank(), "PlatformInfo.name must not be blank")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :composeApp:allTests
```

Expected: compilation error — `PlatformInfo` is unresolved.

- [ ] **Step 3: Write the expect declaration**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/PlatformInfo.kt
package app.hisaab

expect class PlatformInfo() {
    val name: String
}
```

- [ ] **Step 4: Run the test — should still fail (no actual yet)**

```bash
./gradlew :composeApp:allTests
```

Expected: each platform compilation fails because no `actual` declarations exist. Tasks 8 / 9 / 10 fix this.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/PlatformInfo.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/PlatformInfoTest.kt
git commit -m "feat(common): add PlatformInfo expect + failing test"
```

---

### Task 8: `PlatformInfo` actual for Android

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/PlatformInfo.android.kt`

- [ ] **Step 1: Write the actual**

```kotlin
// composeApp/src/androidMain/kotlin/app/hisaab/PlatformInfo.android.kt
package app.hisaab

import android.os.Build

actual class PlatformInfo {
    actual val name: String = "Android ${Build.VERSION.SDK_INT}"
}
```

- [ ] **Step 2: Run the Android unit test**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.PlatformInfoTest"
```

Expected: PASS — Android source set now resolves `PlatformInfo`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/PlatformInfo.android.kt
git commit -m "feat(android): PlatformInfo actual"
```

---

### Task 9: `PlatformInfo` actual for iOS

**Files:**
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/PlatformInfo.ios.kt`

- [ ] **Step 1: Write the actual**

```kotlin
// composeApp/src/iosMain/kotlin/app/hisaab/PlatformInfo.ios.kt
package app.hisaab

import platform.UIKit.UIDevice

actual class PlatformInfo {
    actual val name: String =
        "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
}
```

- [ ] **Step 2: Run iOS simulator tests (host must be macOS)**

```bash
./gradlew :composeApp:iosSimulatorArm64Test
```

Expected: PASS — iOS source set now resolves `PlatformInfo`.

If running on Linux/Windows: skip this step and trust that the iOS source set compiles (verified on a macOS host later). Document this in the PR / handoff.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/iosMain/kotlin/app/hisaab/PlatformInfo.ios.kt
git commit -m "feat(ios): PlatformInfo actual"
```

---

### Task 10: `PlatformInfo` actual for wasmJs

**Files:**
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/PlatformInfo.wasmJs.kt`

- [ ] **Step 1: Write the actual**

```kotlin
// composeApp/src/wasmJsMain/kotlin/app/hisaab/PlatformInfo.wasmJs.kt
package app.hisaab

import kotlinx.browser.window

actual class PlatformInfo {
    actual val name: String = "Web · ${window.navigator.userAgent.take(40)}"
}
```

- [ ] **Step 2: Run wasmJs tests**

```bash
./gradlew :composeApp:wasmJsBrowserTest
```

Expected: PASS — wasmJs source set now resolves `PlatformInfo`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/wasmJsMain/kotlin/app/hisaab/PlatformInfo.wasmJs.kt
git commit -m "feat(wasmJs): PlatformInfo actual"
```

---

## Section C — Design Tokens

### Task 11: `HisaabColors` with TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt
package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HisaabColorsTest {
    @Test
    fun light_cream_background_matches_spec() {
        assertEquals(Color(0xFFFAF7F2), HisaabColors.Light.background)
    }

    @Test
    fun light_terracotta_accent_matches_spec() {
        assertEquals(Color(0xFFAD6B2A), HisaabColors.Light.accent)
    }

    @Test
    fun dark_palette_differs_from_light() {
        assertNotEquals(HisaabColors.Light.background, HisaabColors.Dark.background)
        assertNotEquals(HisaabColors.Light.onBackground, HisaabColors.Dark.onBackground)
    }

    @Test
    fun forest_green_used_for_positive_amounts() {
        assertEquals(Color(0xFF2E7D4F), HisaabColors.Light.positive)
        assertEquals(Color(0xFF2E7D4F), HisaabColors.Dark.positive)
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabColorsTest"
```

Expected: compilation error — `HisaabColors` unresolved.

- [ ] **Step 3: Implement the palette**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt
package app.hisaab.design

import androidx.compose.ui.graphics.Color

object HisaabColors {

    data class Palette(
        val background: Color,
        val surface: Color,
        val onBackground: Color,
        val muted: Color,
        val rule: Color,
        val accent: Color,
        val gold: Color,
        val positive: Color,
        val negative: Color,
    )

    val Light = Palette(
        background = Color(0xFFFAF7F2),
        surface    = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        muted      = Color(0xFF6F6453),
        rule       = Color(0xFFE6DCCB),
        accent     = Color(0xFFAD6B2A),
        gold       = Color(0xFFC8964A),
        positive   = Color(0xFF2E7D4F),
        negative   = Color(0xFFB5402C),
    )

    val Dark = Palette(
        background = Color(0xFF0F0C08),
        surface    = Color(0xFF1A1410),
        onBackground = Color(0xFFF5EDE0),
        muted      = Color(0xFFB3A288),
        rule       = Color(0xFF2A2218),
        accent     = Color(0xFFD68945),
        gold       = Color(0xFFD8A05A),
        positive   = Color(0xFF2E7D4F),
        negative   = Color(0xFFE26B57),
    )
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabColorsTest"
```

Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabColors.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabColorsTest.kt
git commit -m "feat(design): add HisaabColors Light + Dark palettes with tests"
```

---

### Task 12: `HisaabTypography` with TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt
package app.hisaab.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HisaabTypographyTest {
    @Test
    fun display_uses_serif_family() {
        val name = HisaabTypography.display.familyDescription
        assertTrue(
            name.contains("serif", ignoreCase = true),
            "display family should be serif (got: $name)",
        )
    }

    @Test
    fun ui_uses_sans_family() {
        val name = HisaabTypography.ui.familyDescription
        assertTrue(
            name.contains("sans", ignoreCase = true) || name.contains("system", ignoreCase = true),
            "ui family should be sans/system (got: $name)",
        )
    }

    @Test
    fun mono_uses_monospace_family() {
        val name = HisaabTypography.mono.familyDescription
        assertTrue(
            name.contains("mono", ignoreCase = true),
            "mono family should be monospace (got: $name)",
        )
    }

    @Test
    fun heroAmount_is_large_and_regular() {
        val style = HisaabTypography.heroAmount
        assertTrue(style.fontSize.value >= 40f, "heroAmount should be ≥40sp (got ${style.fontSize})")
        assertEquals(FontWeight.Normal, style.fontWeight)
    }

    @Test
    fun tabular_numerals_enabled_on_mono() {
        val features = HisaabTypography.tabular.fontFeatureSettings ?: ""
        assertTrue(
            "tnum" in features,
            "tabular style must enable tnum feature (got: $features)",
        )
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabTypographyTest"
```

Expected: compilation error — `HisaabTypography` unresolved.

- [ ] **Step 3: Implement typography tokens**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt
package app.hisaab.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HisaabTypography {

    data class Family(val familyDescription: String, val compose: FontFamily)

    val display = Family("serif (system fallback; GT Sectra in P0b)", FontFamily.Serif)
    val ui      = Family("system sans (Inter in P0b)", FontFamily.SansSerif)
    val mono    = Family("monospace (JetBrains Mono in P0b)", FontFamily.Monospace)

    val heroAmount: TextStyle = TextStyle(
        fontFamily = display.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 48.sp,
    )

    val title: TextStyle = TextStyle(
        fontFamily = display.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 28.sp,
    )

    val body: TextStyle = TextStyle(
        fontFamily = ui.compose,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val label: TextStyle = TextStyle(
        fontFamily = ui.compose,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
        lineHeight = 12.sp,
    )

    val tabular: TextStyle = TextStyle(
        fontFamily = mono.compose,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum, lnum",
    )
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabTypographyTest"
```

Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTypography.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabTypographyTest.kt
git commit -m "feat(design): add HisaabTypography with serif/sans/mono fallbacks + tests"
```

---

### Task 13: `HisaabSpacing` with TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt
package app.hisaab.design

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HisaabSpacingTest {
    @Test
    fun spacing_scale_uses_4dp_base_grid() {
        assertEquals(4.dp,  HisaabSpacing.xs)
        assertEquals(8.dp,  HisaabSpacing.sm)
        assertEquals(12.dp, HisaabSpacing.md)
        assertEquals(16.dp, HisaabSpacing.lg)
        assertEquals(22.dp, HisaabSpacing.gutter)
        assertEquals(32.dp, HisaabSpacing.xl)
        assertEquals(48.dp, HisaabSpacing.xxl)
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabSpacingTest"
```

Expected: compilation error — `HisaabSpacing` unresolved.

- [ ] **Step 3: Implement**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt
package app.hisaab.design

import androidx.compose.ui.unit.dp

object HisaabSpacing {
    val xs     = 4.dp
    val sm     = 8.dp
    val md     = 12.dp
    val lg     = 16.dp
    val gutter = 22.dp
    val xl     = 32.dp
    val xxl    = 48.dp
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.design.HisaabSpacingTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabSpacing.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/design/HisaabSpacingTest.kt
git commit -m "feat(design): add HisaabSpacing 4dp base grid + 22dp gutter"
```

---

### Task 14: `HisaabShapes`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt
package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HisaabShapes {
    val card  = RoundedCornerShape(10.dp)
    val sheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val pill  = RoundedCornerShape(999.dp)

    fun material3(): Shapes = Shapes(
        small  = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large  = RoundedCornerShape(18.dp),
    )
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinMetadata
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabShapes.kt
git commit -m "feat(design): add HisaabShapes corner radii"
```

---

### Task 15: `HisaabTheme` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt
package app.hisaab.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHisaabPalette = staticCompositionLocalOf<HisaabColors.Palette> {
    error("HisaabTheme not provided")
}

@Composable
fun HisaabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) HisaabColors.Dark else HisaabColors.Light

    val material3Colors: ColorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.gold,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.gold,
            tertiary = palette.positive,
            error = palette.negative,
            outline = palette.rule,
        )
    }

    val material3Typography = Typography(
        displayLarge   = HisaabTypography.heroAmount,
        headlineMedium = HisaabTypography.title,
        bodyMedium     = HisaabTypography.body,
        labelSmall     = HisaabTypography.label,
        bodySmall      = HisaabTypography.tabular,
    )

    CompositionLocalProvider(LocalHisaabPalette provides palette) {
        MaterialTheme(
            colorScheme = material3Colors,
            typography = material3Typography,
            shapes = HisaabShapes.material3(),
            content = content,
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinMetadata
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/design/HisaabTheme.kt
git commit -m "feat(design): add HisaabTheme wrapper over Material3"
```

---

## Section D — Today Screen + App Root

### Task 16: Empty `TodayScreen` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt
package app.hisaab.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.HisaabTypography
import app.hisaab.design.LocalHisaabPalette

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = HisaabSpacing.gutter),
        ) {
            Spacer(Modifier.height(HisaabSpacing.lg))
            Text(
                text = "Today",
                style = HisaabTypography.title,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(HisaabSpacing.xs))
            Text(
                text = "Your ledger lives here. Empty for now — first entries arrive in P1.",
                style = HisaabTypography.body,
                color = palette.muted,
            )
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinMetadata
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt
git commit -m "feat(today): add empty Today screen with editorial header"
```

---

### Task 17: Skipped — UI screenshot tests deferred to P0b

Compose Multiplatform UI test runners require additional setup (compose-ui-test-junit4 in androidMain) that is overkill for an empty screen. We rely on the manual platform-run verification in Section H. Skip this task and proceed to Task 18.

---

### Task 18: `App()` root composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/App.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/commonMain/kotlin/app/hisaab/App.kt
package app.hisaab

import androidx.compose.runtime.Composable
import app.hisaab.design.HisaabTheme
import app.hisaab.screens.today.TodayScreen

@Composable
fun App() {
    HisaabTheme {
        TodayScreen()
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinMetadata
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/App.kt
git commit -m "feat(app): add App() root composable"
```

---

## Section E — Android entry point

### Task 19: `HisaabApplication` class

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/HisaabApplication.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/androidMain/kotlin/app/hisaab/HisaabApplication.kt
package app.hisaab

import android.app.Application

class HisaabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase P0b: initialize SQLDelight, Supabase client, crash reporter
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/HisaabApplication.kt
git commit -m "feat(android): add Application subclass"
```

---

### Task 20: `AndroidManifest.xml`

**Files:**
- Create: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Write the manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".HisaabApplication"
        android:label="Hisaab"
        android:icon="@android:drawable/sym_def_app_icon"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|density|fontScale|keyboard|navigation|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

> The system-drawable icon is a placeholder. Replace with a real `mipmap-anydpi-v26/ic_launcher.xml` in P0b or earlier when brand assets exist.

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/androidMain/AndroidManifest.xml
git commit -m "feat(android): add AndroidManifest with MainActivity launcher"
```

---

### Task 21: `MainActivity`

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/MainActivity.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/androidMain/kotlin/app/hisaab/MainActivity.kt
package app.hisaab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
```

- [ ] **Step 2: Verify Android assembleDebug**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. APK at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/MainActivity.kt
git commit -m "feat(android): add MainActivity hosting App()"
```

---

### Task 22: Run on an Android emulator or device

**Files:** none — verification only

- [ ] **Step 1: Start an Android emulator (API 34+)**

```bash
emulator -list-avds
emulator -avd <avd-name> &
```

Or use Android Studio's Device Manager.

- [ ] **Step 2: Install and launch**

```bash
./gradlew :composeApp:installDebug
adb shell am start -n app.hisaab/.MainActivity
```

- [ ] **Step 3: Verify the cream Today screen renders**

Expected on-device:
- Cream background (`#FAF7F2`)
- "Today" header in serif (system fallback)
- Subtitle text below ("Your ledger lives here. Empty for now — first entries arrive in P1.")
- No crashes

Capture a screenshot:

```bash
adb exec-out screencap -p > /tmp/p0a-android.png
```

- [ ] **Step 4: No commit (verification only — record the screenshot path in the PR description)**

---

## Section F — iOS entry point

### Task 23: Create the `iosApp/` Xcode project skeleton

**Files:**
- Create (via Xcode): `iosApp/iosApp.xcodeproj/project.pbxproj`, `iosApp/iosApp/Info.plist`, `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`
- Create: `iosApp/Configuration/Config.xcconfig`

`project.pbxproj` is impractical to hand-write — use Xcode to scaffold it.

- [ ] **Step 1: Generate via Xcode**

```
Xcode → File → New → Project
  → iOS → App
  → Product Name: iosApp
  → Organization Identifier: app.hisaab
  → Interface: SwiftUI
  → Language: Swift
  → Save to: /Users/<you>/projects/finance-app/iosApp/
```

Xcode generates `.xcodeproj`, `iOSApp.swift`, `ContentView.swift`, `Info.plist`, `Assets.xcassets/`.

- [ ] **Step 2: Add `Configuration/Config.xcconfig`**

```xcconfig
// iosApp/Configuration/Config.xcconfig
TEAM_ID =
BUNDLE_ID = app.hisaab
APP_NAME = Hisaab
IPHONEOS_DEPLOYMENT_TARGET = 17.0
PRODUCT_BUNDLE_IDENTIFIER = $(BUNDLE_ID)
PRODUCT_NAME = $(APP_NAME)
ENABLE_USER_SCRIPT_SANDBOXING = NO
```

In Xcode → Project → Info → Configurations: set both Debug and Release to use `Config.xcconfig`.

- [ ] **Step 3: Add a Run Script build phase that builds the KMP framework**

In Xcode → iosApp target → Build Phases → `+ New Run Script Phase`, place ABOVE Compile Sources:

```bash
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

- [ ] **Step 4: Run once to produce `ComposeApp.framework` on disk**

```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode -Pkotlin.native.cacheKind=none
```

Expected: framework appears under `composeApp/build/xcode-frameworks/`.

- [ ] **Step 5: Add `ComposeApp.framework` to "Frameworks, Libraries, and Embedded Content"**

In Xcode → iosApp target → General → Frameworks → `+ Add Other → Add Files...` → select the framework from `composeApp/build/xcode-frameworks/ComposeApp.framework`. Set Embed = "Embed & Sign".

- [ ] **Step 6: Commit**

```bash
git add iosApp/
git commit -m "feat(ios): add iosApp Xcode project + xcconfig + framework hook"
```

---

### Task 24: `MainViewController.kt` bridging Kotlin to UIKit

**Files:**
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/MainViewController.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/iosMain/kotlin/app/hisaab/MainViewController.kt
package app.hisaab

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
```

- [ ] **Step 2: Verify iOS framework builds**

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: framework at `composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/iosMain/kotlin/app/hisaab/MainViewController.kt
git commit -m "feat(ios): expose MainViewController for UIKit hosting"
```

---

### Task 25: SwiftUI bridge — `iOSApp.swift` + `ContentView.swift`

**Files:**
- Replace: `iosApp/iosApp/iOSApp.swift`
- Replace: `iosApp/iosApp/ContentView.swift`

- [ ] **Step 1: Replace `iOSApp.swift`**

```swift
// iosApp/iosApp/iOSApp.swift
import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
```

- [ ] **Step 2: Replace `ContentView.swift`**

```swift
// iosApp/iosApp/ContentView.swift
import SwiftUI
import UIKit
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // no-op
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add iosApp/iosApp/iOSApp.swift iosApp/iosApp/ContentView.swift
git commit -m "feat(ios): SwiftUI bridge to Compose via UIViewControllerRepresentable"
```

---

### Task 26: Build + run on iOS simulator

**Files:** none — verification only

- [ ] **Step 1: Launch iOS simulator (iPhone 16, iOS 17+)**

```bash
xcrun simctl boot "iPhone 16" || true
open -a Simulator
```

- [ ] **Step 2: Build and run from Xcode**

In Xcode: select the `iOSApp` scheme, pick "iPhone 16" destination, hit Run (⌘R).

Or from CLI:

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```

- [ ] **Step 3: Verify the cream Today screen renders on the simulator**

Expected: cream background, "Today" header in serif (San Francisco serif fallback), subtitle text. No crashes.

Capture screenshot:

```bash
xcrun simctl io booted screenshot /tmp/p0a-ios.png
```

- [ ] **Step 4: No commit (verification only — record screenshot in PR)**

---

## Section G — Web (wasmJs) entry point

### Task 27: `main.kt` entry for wasmJs

**Files:**
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/main.kt`

- [ ] **Step 1: Implement**

```kotlin
// composeApp/src/wasmJsMain/kotlin/app/hisaab/main.kt
package app.hisaab

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/wasmJsMain/kotlin/app/hisaab/main.kt
git commit -m "feat(web): wasmJs entry point via ComposeViewport"
```

---

### Task 28: `index.html` shell

**Files:**
- Create: `composeApp/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Write the HTML shell**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Hisaab</title>
  <style>
    html, body {
      margin: 0;
      padding: 0;
      height: 100%;
      background: #faf7f2;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    body {
      display: flex;
      align-items: stretch;
      justify-content: stretch;
    }
    .loading {
      position: fixed;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: Georgia, "Times New Roman", serif;
      font-size: 18px;
      color: #6f6453;
      pointer-events: none;
    }
  </style>
</head>
<body>
  <div class="loading" id="loading">Loading…</div>
  <script type="application/javascript">
    const observer = new MutationObserver(() => {
      const loader = document.getElementById('loading');
      if (loader && document.body.children.length > 1) {
        loader.remove();
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true });
  </script>
  <script src="composeApp.js"></script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/wasmJsMain/resources/index.html
git commit -m "feat(web): add index.html shell with cream loading state"
```

---

### Task 29: Build + run in the browser

**Files:** none — verification only

- [ ] **Step 1: Run the dev server**

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Expected: webpack dev server starts on `http://localhost:8080/` (port may vary — check output).

- [ ] **Step 2: Open the URL in a Chromium-based browser**

Expected: cream page with "Today" header in serif (system fallback) and subtitle. No console errors.

Capture screenshot:

```bash
chromium --headless --screenshot=/tmp/p0a-web.png --window-size=390,844 http://localhost:8080/
```

- [ ] **Step 3: Build production artifact**

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

Expected: artifacts in `composeApp/build/dist/wasmJs/productionExecutable/` including `composeApp.js`, `composeApp.wasm`, `index.html`. Record the total transferred size for the risk register.

- [ ] **Step 4: No commit (verification only — record screenshot + bundle size in PR)**

---

## Section H — Final verification

### Task 30: Run all unit tests across targets

**Files:** none — verification only

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew :composeApp:allTests
```

Expected: all design-token tests pass (Colors, Typography, Spacing, PlatformInfo) on every platform that supports tests (jvm, iosSimulatorArm64, wasmJs). No failures.

- [ ] **Step 2: Open the combined HTML report**

```bash
open composeApp/build/reports/tests/allTests/index.html   # macOS
xdg-open composeApp/build/reports/tests/allTests/index.html  # linux
```

Expected: report shows 100% pass.

- [ ] **Step 3: No commit (verification only)**

---

### Task 31: Document the build + add `README.md`

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write the README**

````markdown
# Hisaab

Privacy-first AI financial guide. Bangladesh-first commercial SaaS.

**Documents:**
- [Product idea + business plan](./docs/idea.md)
- [Master architecture spec](./docs/superpowers/specs/2026-05-27-hisaab-master-architecture-design.md)
- [Phase P0a plan (this build)](./docs/superpowers/plans/2026-05-27-phase-p0a-foundation.md)

**Current phase:** P0a — Project scaffold + design system + empty UI shell.

## Build

```bash
./gradlew :composeApp:assembleDebug                              # Android APK
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode         # iOS framework (then build iosApp.xcodeproj)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun                # Web dev server
./gradlew :composeApp:allTests                                   # All unit tests
```

## Stack

Kotlin Multiplatform · Compose Multiplatform · Material 3 · kotlin.test.
````

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README pointing to design docs + plans"
```

---

## Done criteria for Phase P0a

All of the following must be true before declaring P0a complete:

- [ ] `./gradlew :composeApp:allTests` passes on a developer macOS box
- [ ] Android emulator (API 34+) launches the app; cream Today screen renders
- [ ] iOS simulator (iPhone 16, iOS 17+) launches the app; cream Today screen renders
- [ ] Chromium browser loads the wasmJs build at `http://localhost:8080`; cream Today screen renders
- [ ] Three platform screenshots are attached to the PR or noted in the merge commit
- [ ] No `.DS_Store`, `local.properties`, or `xcuserdata/` is tracked
- [ ] `git log --oneline` shows ~30 small commits, one per task

## What's next (P0b plan)

After P0a is verified and merged:

- SQLDelight + SQLCipher local database (with first `User` table)
- Supabase Kotlin SDK setup
- Phone OTP signup flow (Supabase Auth)
- Onboarding screen with passphrase setup
- E2EE key derivation (Argon2id) hooked to local Keychain / Keystore

P0b ships the verification "user can sign up; app remembers them across cold start" — completing the M1 milestone in the master spec.
