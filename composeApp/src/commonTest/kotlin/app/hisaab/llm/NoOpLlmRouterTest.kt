package app.hisaab.llm

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class NoOpLlmRouterTest {

    @Test
    fun `active returns null so pipeline runs template-only`() = runTest {
        val router: LlmRouter = NoOpLlmRouter()
        assertNull(router.active())
    }
}
