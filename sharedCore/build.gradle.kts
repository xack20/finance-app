import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :sharedCore — minimal multiplatform core shared between :composeApp (Android/iOS) and the macOS
// helper (:macHelper, JVM). Holds ONLY crypto + domain + relay-codec so the helper can reuse the
// EXACT sealing logic (byte-identical ciphertext) without pulling in Compose/UI/data. Deps are kept
// to libsodium + kotlinx-serialization on purpose.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.libsodium.kmp)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "app.hisaab.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
