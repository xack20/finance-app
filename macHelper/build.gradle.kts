import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :macHelper — the macOS companion (P3). Reads SMS that iOS forwards to this Mac (Messages
// chat.db), seals each capture with the SAME :sharedCore relay crypto, and pushes it E2EE to
// Supabase relay_captures. Compose Desktop JVM app; distributed notarized OUTSIDE the App Store
// (it needs Full Disk Access to read chat.db, which the sandbox forbids).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":sharedCore")) // crypto + RawCapture + RelayCaptureCrypto
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.sqlite.jdbc) // read ~/Library/Messages/chat.db
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.hisaab.mac.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "HisaabCompanion"
            packageVersion = "1.0.0"
        }
    }
}
