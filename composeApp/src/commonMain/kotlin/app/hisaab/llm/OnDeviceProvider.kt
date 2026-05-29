package app.hisaab.llm

/**
 * Platform on-device LLM provider. Android returns a MediaPipe/AICore-backed
 * provider when a model is available; iOS and wasm return null (the iOS Foundation
 * Models path is a later slice). Callers must treat null as "no on-device engine".
 */
expect fun createOnDeviceProvider(): LlmProvider?
