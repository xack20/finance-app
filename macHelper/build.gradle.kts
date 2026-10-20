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
            implementation(libs.angus.mail) // IMAP email (jakarta.mail)
            implementation(libs.supabase.auth) // phone-OTP login (same account as the app)
            implementation(libs.supabase.postgrest) // push to relay_captures
            implementation(libs.ktor.client.cio) // JVM HTTP engine for Supabase
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// supabase-kt 3.0.3 is compiled against kotlinx.datetime.Instant — a REAL class in 0.6.x. Compose
// material3-desktop 1.9.0-beta03 drags in kotlinx-datetime 0.7.1, where that type was moved to the
// stdlib (kotlin.time.Instant) and `kotlinx/datetime/Instant.class` no longer exists. On the JVM that
// surfaces as NoClassDefFoundError while parsing the auth session (e.g. on OTP verify). Pin to the
// catalog's declared 0.6.1 so the Supabase SDK finds the class it was built against. (Material3's only
// datetime use is date pickers, which the companion UI doesn't have.)
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
    }
}

compose.desktop {
    application {
        mainClass = "app.hisaab.mac.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "HisaabCompanion"
            packageVersion = "1.0.0"

            // Bundle the full JDK module set. Compose Desktop's jlink runtime otherwise omits modules
            // that are only reached via reflection/JDBC: sqlite-jdbc needs `java.sql` (DriverManager)
            // to read chat.db, and jakarta.mail/TLS pull `java.desktop` + crypto modules for IMAP.
            // Size is irrelevant for a local companion; this avoids per-module whack-a-mole.
            includeAllModules = true

            macOS {
                bundleID = "app.hisaab.companion"
                // Hardened-runtime entitlements: this JVM app JITs and loads bundled native libs
                // (libsodium, sqlite-jdbc), which notarization's hardened runtime blocks by default.
                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("entitlements.plist"))

                // Sign + notarize ONLY when a Developer ID is provided via env, so an unsigned
                // packageDmg still works for local testing and no secret ever lives in the repo.
                val signIdentity = System.getenv("HISAAB_MAC_SIGN_IDENTITY")
                if (!signIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(signIdentity)
                    }
                    notarization {
                        appleID.set(System.getenv("HISAAB_MAC_APPLE_ID").orEmpty())
                        password.set(System.getenv("HISAAB_MAC_NOTARY_PASSWORD").orEmpty())
                        teamID.set(System.getenv("HISAAB_MAC_TEAM_ID").orEmpty())
                    }
                }
            }
        }
    }
}
