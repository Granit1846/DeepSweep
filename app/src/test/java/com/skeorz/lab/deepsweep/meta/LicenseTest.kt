package com.skeorz.lab.deepsweep.meta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseTest {
    private val pair: KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey: ByteArray = pair.public.encoded

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun sign(payload: String): String {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return "DSWP1.${b64(payload.toByteArray(Charsets.UTF_8))}.${b64(signature)}"
    }

    @Test
    fun acceptsCodeSignedByTheSeller() {
        assertTrue(License.verify(sign("v1|deepsweep.pro|DS-0001|1762000000"), publicKey))
    }

    @Test
    fun acceptsCodeWithSpacesAndLineBreaks() {
        val code = sign("v1|deepsweep.pro|DS-0002|1762000000")
        val broken = code.chunked(40).joinToString("\n ")
        assertTrue(License.verify(broken, publicKey))
    }

    @Test
    fun rejectsTamperedPayload() {
        val code = sign("v1|deepsweep.pro|DS-0003|1762000000")
        val parts = code.split('.')
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("v1|deepsweep.pro|DS-9999|1762000000".toByteArray())
        assertFalse(License.verify("DSWP1.$payload.${parts[2]}", publicKey))
    }

    @Test
    fun rejectsCodeForAnotherProduct() {
        assertFalse(License.verify(sign("v1|another.game|DS-0004|1762000000"), publicKey))
    }

    @Test
    fun rejectsAnotherKeyPair() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val payload = "v1|deepsweep.pro|DS-0005|1762000000"
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(other.private)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        val code = "DSWP1.${b64(payload.toByteArray())}.${b64(signature)}"
        assertFalse(License.verify(code, publicKey))
    }

    @Test
    fun rejectsGarbage() {
        assertFalse(License.verify(null, publicKey))
        assertFalse(License.verify("", publicKey))
        assertFalse(License.verify("привет", publicKey))
        assertFalse(License.verify("DSWP1.%%%.###", publicKey))
        assertFalse(License.verify("DSWP2.abc.def", publicKey))
    }
}
