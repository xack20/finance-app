package app.hisaab.data
import app.hisaab.domain.TxnSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TxnSourceMapperTest {
    @Test fun `CHAT is a known source`() {
        assertEquals(TxnSource.CHAT, TxnSource.valueOf("CHAT"))
    }
    @Test fun `unknown source falls back to MANUAL`() {
        assertEquals(TxnSource.MANUAL, txnSourceOrManual("SOME_FUTURE_SOURCE"))
    }
    @Test fun `known source parses normally`() {
        assertEquals(TxnSource.VOICE, txnSourceOrManual("VOICE"))
    }
}
