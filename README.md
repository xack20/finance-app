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
