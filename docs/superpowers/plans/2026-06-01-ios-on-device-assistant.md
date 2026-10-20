# iOS on-device assistant (Apple Foundation Models) + cloud fallback

**Date:** 2026-06-01
**Target:** iPhone 15 Pro / iOS 26, Apple Intelligence. On-device preferred, cloud (BYO key) as fallback.
**Branch:** `chore/ios-device-build`

## Goal
Run the conversational assistant on-device via Apple's `FoundationModels` (`LanguageModelSession`) when
available — private, free, no API key, offline — and fall back to the existing cloud providers
(Claude/Gemini/OpenAI) when on-device is unavailable (older OS, Apple Intelligence off, not eligible).

## Constraint
`FoundationModels` is a **Swift-only** framework (Swift macros, `LanguageModelSession`). Kotlin/Native
cinterop can't call it. So the model call lives in **Swift**, exposed to Kotlin through a tiny bridge.

## Design (no commonMain changes)

### 1. Kotlin bridge (iosMain)
- `interface NativeAgentBridge { fun isAvailable(): Boolean; fun complete(prompt, maxTokens, callback: (String?) -> Unit) }`
  — callback-based so Swift never implements a Kotlin `suspend` fun.
- `object NativeAgentRegistry { var bridge: NativeAgentBridge? }` — Swift sets this once at launch.
- `class OnDeviceAgentProvider(bridge) : AgentProvider` — adapts the callback bridge into the
  `suspend complete(messages)` the `AgentLoop` already drives (ReAct over text; no native tool-calling
  needed for v1). Formats the message list into a single prompt.

### 2. iOS AppContainer wiring (iosMain)
- `agentProvider = { onDevice() ?: cloud() }` — prefer on-device, else the current cloud resolution.
- `isConsented = { onDeviceAvailable() || agent_consent_at != null }` — on-device needs **no** cloud
  consent (nothing leaves the device); cloud path still gated by consent + key.
- `unavailableReason` updated to mention on-device.

### 3. Swift (iosApp)
- `OnDeviceAgentBridge: NSObject, NativeAgentBridge` (`@available(iOS 26)`) → `SystemLanguageModel.default.availability`
  + `LanguageModelSession().respond(to:)`. Returns `nil` on any error → caller falls back / surfaces a message.
- `iOSApp.init`: `if #available(iOS 26) { NativeAgentRegistry.shared.bridge = OnDeviceAgentBridge() }`.
- Link `FoundationModels` (weak; availability-gated, deployment target stays 15.0).

## Tradeoffs (documented)
- On-device model ≈ 3B params, 4,096-token context: great for chat; less reliable than cloud for long
  multi-tool ReAct loops. Cloud fallback covers heavy reasoning.
- Requires iOS 26 + Apple Intelligence enabled + eligible device (15 Pro = A17 Pro, eligible).

## Verify
- iOS Kotlin compile + full app build (Swift links FoundationModels).
- Runtime on-device path needs Apple Intelligence (device/sim); fallback path testable without it.

## Out of scope (v1)
- Native FoundationModels `Tool`/`@Generable` tool-calling (Kotlin `AgentLoop` handles tools over text).
- A Settings engine picker (auto-prefer on-device for now; add a status row later).
