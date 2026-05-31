````markdown
# Hisaab

Privacy-first AI financial guide. Bangladesh-first commercial SaaS.

**Documents:**
- [Product idea + business plan](./docs/idea.md)
- [Master architecture spec](./docs/superpowers/specs/2026-05-27-hisaab-master-architecture-design.md)
- [Phase P0a plan (foundation)](./docs/superpowers/plans/2026-05-27-phase-p0a-foundation.md)
- [Midnight redesign spec](./docs/superpowers/specs/2026-05-31-midnight-redesign-design.md)
- [Design system context](./docs/design/PROJECT-CONTEXT.md)

**Current phase:** Midnight redesign — full dark "Midnight" re-skin across every surface (theme/tokens → onboarding/lock → accessibility polish), Phases 1–9 (PR #4). Feature milestones through M4 (SMS capture, conversational agent) are shipped; see [tech-debt.md](./docs/tech-debt.md) for tracked carryover.

## Build

```bash
./gradlew :composeApp:assembleDebug                              # Android APK
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode         # iOS framework (then build iosApp.xcodeproj)
./gradlew -Phisaab.enableWasm=true :composeApp:wasmJsBrowserDevelopmentRun   # Web dev (wasm gated off by default)
./gradlew :composeApp:allTests                                   # All unit tests
```

## Stack

Kotlin Multiplatform · Compose Multiplatform · "Midnight" design system on a Material 3 foundation · SQLDelight + SQLCipher · Supabase · kotlin.test.
````
