import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

// wasmJs is gated off by default — libsodium (all crypto) publishes no wasmJs artifact
// in any version, a hard upstream blocker. Re-enable with -Phisaab.enableWasm=true.
val enableWasm = (findProperty("hisaab.enableWasm") as String?)?.toBoolean() ?: false

// Pin kotlinx-datetime to 0.6.1 on ALL targets. iOS otherwise floats to 0.7.1 (where Clock/Instant
// moved out of package kotlinx.datetime into kotlin.time), breaking the 27 commonMain files that call
// kotlinx.datetime.Clock.System. Android already resolves 0.6.1 (Supabase pins it); a non-strict
// commonMain declaration loses Gradle's highest-version-wins, so force it everywhere.
configurations.all {
    resolutionStrategy { force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1") }
}

kotlin {
    compilerOptions {
        // Silence KT-61573 "expect/actual class in Beta" warnings until Kotlin stabilizes it.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
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

    if (enableWasm) {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs {
            outputModuleName = "composeApp"
            browser {
                commonWebpackConfig {
                    outputFileName = "composeApp.js"
                }
            }
            binaries.executable()
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.biometric)
            implementation(libs.mediapipe.genai)
            // play-services-aicore:16.0.0-alpha05 not available in Google Maven;
            // AICore availability is a runtime class-probe only (no compile dep needed).
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        if (enableWasm) {
            named("wasmJsMain").dependencies {
                implementation(libs.sqldelight.web.driver)
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.libsodium.kmp)
            implementation(libs.nav.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.mock)
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.espresso.core)
                implementation(libs.compose.ui.test.junit4)
            }
        }
    }
}

dependencies {
    debugImplementation(libs.compose.ui.test.manifest)
}

android {
    namespace = "app.hisaab"
    compileSdk = 35

    // android.test.mock (MockContentResolver) — used by CaptureServiceBackfillInstrumentedTest to
    // host a fake ContentProvider in-process. Optional platform library; not packaged into the app.
    useLibrary("android.test.mock")

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "app.hisaab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-p0a"
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) load(f.inputStream())
        }
        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("supabase.anon_key", "")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

sqldelight {
    // Do NOT link the system libsqlite3 on native (iOS): the iosApp links the SQLCipher pod instead,
    // so the native driver's sqlite3 symbols resolve against SQLCipher and PRAGMA key actually
    // encrypts. (Android is unaffected — it uses the SQLCipher android-driver.) This is an
    // extension-level setting, not per-database.
    linkSqlite.set(false)
    databases {
        create("HisaabDatabase") {
            packageName.set("app.hisaab.db")
            deriveSchemaFromMigrations.set(true)
            verifyMigrations.set(true)
        }
    }
}
