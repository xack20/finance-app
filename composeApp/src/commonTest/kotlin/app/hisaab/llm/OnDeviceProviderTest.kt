package app.hisaab.llm

import kotlin.test.Test

class OnDeviceProviderTest {
    @Test
    fun `createOnDeviceProvider is callable from common`() {
        // On JVM/Android unit-test host the model is absent, so this is null or a
        // provider whose isAvailable() is false. We only assert it compiles + runs.
        createOnDeviceProvider()
    }
}
