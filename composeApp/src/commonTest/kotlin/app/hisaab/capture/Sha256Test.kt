package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256Test {

    @Test
    fun `known vector for empty string`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexOf(""),
        )
    }

    @Test
    fun `known vector for abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexOf("abc"),
        )
    }

    @Test
    fun `different inputs produce different hashes`() {
        assertNotEquals(Sha256.hexOf("bKash|Payment Tk 1"), Sha256.hexOf("bKash|Payment Tk 2"))
    }
}
