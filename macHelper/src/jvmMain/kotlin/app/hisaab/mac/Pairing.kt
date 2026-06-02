package app.hisaab.mac

import app.hisaab.capture.RelayCaptureCrypto
import app.hisaab.crypto.MnemonicService

/**
 * Pairs the Mac with the phone's account by deriving the SAME relay key from the user's recovery
 * phrase (the 24 BIP39 words backed up on the phone). The phrase is never stored — only the derived
 * key is kept in memory (and, by the UI, in the macOS Keychain). Identical derivation to the phone:
 * words → master secret ([MnemonicService.decode]) → relay key ([RelayCaptureCrypto.relayKey]).
 */
object Pairing {

    /** Split a typed recovery phrase into normalized lowercase words. */
    fun words(phrase: String): List<String> =
        phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * Derive the 32-byte relay key from a recovery phrase. Throws (via MnemonicService) on a wrong
     * word count, an unknown word, or a failed BIP39 checksum — surfaced to the user as "invalid
     * recovery phrase".
     */
    fun relayKeyFromPhrase(
        phrase: String,
        mnemonic: MnemonicService = MnemonicService(),
        crypto: RelayCaptureCrypto = RelayCaptureCrypto(),
    ): ByteArray {
        val masterSecret = mnemonic.decode(words(phrase))
        return crypto.relayKey(masterSecret)
    }
}
