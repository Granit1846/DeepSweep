package com.skeorz.lab.deepsweep.meta

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Offline licence codes for the Pro build.
 *
 * A code looks like `DSWP1.<payload>.<signature>`: the payload is UTF-8 text
 * `v1|deepsweep.pro|<order id>|<issued at>`, and the signature is ECDSA P-256 /
 * SHA-256 over those payload bytes. Only the seller's private key can create codes,
 * so the app verifies them without a server, an account or network access.
 *
 * The key pair lives in `tools/license/keys/` (git-ignored); the public half is
 * embedded here. If the pair is ever regenerated, an app update must replace
 * [PUBLIC_KEY] and stay able to verify both keys at least during the transition.
 */
object License {
    private const val PREFIX = "DSWP1"
    private const val VERSION = "v1"
    private const val PRODUCT = "deepsweep.pro"

    /** Public half of the key made by `tools/license/gen-keys.sh` (X.509 SubjectPublicKeyInfo). */
    private const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDiJd+P6uekfwtukWhJ/vePDRik28pKAo1RfArg4Q/yQAB55BQnFpiKOGRywWsOXoRhFHE1nDuHBjNE8qq9t1Qw=="

    fun isValid(code: String?): Boolean = verify(code, Base64.getDecoder().decode(PUBLIC_KEY))

    /** Shared with unit tests: checks [code] against an arbitrary X.509 public key. */
    internal fun verify(code: String?, publicKey: ByteArray): Boolean = runCatching {
        val parts = code.orEmpty().filterNot { it.isWhitespace() }.split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return false
        val payload = Base64.getUrlDecoder().decode(parts[1])
        val signature = Base64.getUrlDecoder().decode(parts[2])
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
        val valid = Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(payload)
            verify(signature)
        }
        if (!valid) return false
        val fields = String(payload, Charsets.UTF_8).split('|')
        fields.size >= 4 && fields[0] == VERSION && fields[1] == PRODUCT
    }.getOrDefault(false)
}
