package app.hisaab.capture

import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.domain.RawCapture
import kotlinx.serialization.json.Json

/**
 * E2EE codec for the Mac→phone capture relay
 * (see docs/superpowers/specs/2026-06-02-mac-sms-companion-relay-design.md).
 *
 * A [RawCapture] read on the Mac is serialized to canonical JSON and sealed with
 * XChaCha20-Poly1305 under a **relay key** derived from the user's 32-byte master secret
 * (the same secret both devices derive from the recovery-phrase mnemonic). Only the
 * (nonce, ciphertext) pair is uploaded to Supabase — the server is zero-knowledge of the
 * SMS content. The phone derives the identical relay key from the same master secret and
 * opens the blob, then feeds the [RawCapture] into the existing CapturePipeline.
 *
 * This is a thin wrapper over the existing [CryptoService.hkdf] (key derivation) and
 * [BlobCrypto] (AEAD) — no new crypto primitives are introduced.
 */
class RelayCaptureCrypto(
    private val crypto: CryptoService = CryptoService(),
    private val blob: BlobCrypto = BlobCrypto(),
    private val json: Json = Json,
) {
    /**
     * Derive the 32-byte relay key from the 32-byte master secret. Deterministic, so the Mac
     * and the phone independently arrive at the same key with no key exchange. The [RELAY_INFO]
     * label domain-separates this from other keys derived off the same secret (e.g. the DB key).
     */
    fun relayKey(masterSecret: ByteArray): ByteArray =
        crypto.hkdf(masterSecret, RELAY_INFO, RELAY_KEY_LEN)

    /** Seal a capture into the (nonce, ciphertext) pair stored in a `relay_captures` row. */
    fun seal(capture: RawCapture, relayKey: ByteArray): SealedCapture {
        val plaintext = json.encodeToString(RawCapture.serializer(), capture).encodeToByteArray()
        val (ciphertext, nonce) = blob.encrypt(plaintext, relayKey)
        return SealedCapture(nonce = nonce, ciphertext = ciphertext)
    }

    /** Open a relay row back into a capture. Throws if the key/nonce/ciphertext fail authentication. */
    fun open(sealed: SealedCapture, relayKey: ByteArray): RawCapture {
        val plaintext = blob.decrypt(sealed.ciphertext, relayKey, sealed.nonce)
        return json.decodeFromString(RawCapture.serializer(), plaintext.decodeToString())
    }

    companion object {
        /** Domain-separation label for the relay key (versioned so we can rotate the scheme). */
        val RELAY_INFO: ByteArray = "hisaab/relay/v1".encodeToByteArray()
        const val RELAY_KEY_LEN: Int = 32
    }
}

/**
 * The encrypted form of a relayed capture: a 24-byte XChaCha20-Poly1305 [nonce] and the
 * [ciphertext] (which includes the AEAD tag). These map 1:1 to the `relay_captures` columns.
 * Not a data class — [ByteArray] would give reference-based equals/hashCode; callers compare
 * the decrypted [RawCapture], not the sealed form.
 */
class SealedCapture(val nonce: ByteArray, val ciphertext: ByteArray)
