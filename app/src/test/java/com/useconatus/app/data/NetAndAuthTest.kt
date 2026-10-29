package com.useconatus.app.data

import com.useconatus.app.data.net.allowsPlainHttp
import com.useconatus.app.data.auth.Pkce
import com.useconatus.app.data.auth.TokenCipher
import com.useconatus.app.data.net.errorMessageFrom
import com.useconatus.app.data.net.isLocalNetwork
import com.useconatus.app.data.net.isOnLocalNetwork
import com.useconatus.app.data.net.normalizeServerUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator

class ServerUrlTest {
    @Test fun `adds https when no scheme is given`() =
        assertEquals("https://conatus.example.com/", normalizeServerUrl("conatus.example.com").toString())

    @Test fun `keeps an explicit http address for a local server`() =
        assertEquals("http://10.0.2.2:3000/", normalizeServerUrl("http://10.0.2.2:3000").toString())

    @Test fun `trims spaces and slashes and lowercases the host`() =
        assertEquals("https://conatus.example.com/", normalizeServerUrl("  https://Conatus.Example.com//  ").toString())

    @Test fun `keeps a sub-path with a trailing slash`() =
        assertEquals("https://example.com/conatus/", normalizeServerUrl("example.com/conatus").toString())

    @Test fun `rejects things that aren't web addresses`() {
        listOf("", "   ", "ftp://example.com", "https://exa mple.com", "https://example.com/?a=1", "https://").forEach {
            assertNull("\"$it\" should be rejected", normalizeServerUrl(it))
        }
    }
}

class PkceTest {
    @Test fun `challenge matches the RFC 7636 example`() =
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))

    @Test fun `verifier fits the server's 43 to 128 character rule`() {
        val verifier = Pkce.verifier(SecureRandom())
        assertEquals(43, verifier.length)
        assertTrue(verifier.matches(Regex("^[A-Za-z0-9._~-]{43,128}$")))
        // The server's challenge check is exactly 43 base64url characters.
        assertTrue(Pkce.challenge(verifier).matches(Regex("^[A-Za-z0-9_-]{43}$")))
    }

    @Test fun `verifiers are random`() = assertNotEquals(Pkce.verifier(), Pkce.verifier())
}

class TokenCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = TokenCipher { key }

    @Test fun `round trips a token`() {
        val stored = cipher.encrypt("tdc_secret")
        assertNotEquals("tdc_secret", stored)
        assertEquals("tdc_secret", cipher.decrypt(stored))
    }

    @Test fun `uses a fresh IV each time`() = assertNotEquals(cipher.encrypt("tdc_x"), cipher.encrypt("tdc_x"))

    @Test fun `tampered data decrypts to null instead of throwing`() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt("tdc_secret"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
        assertNull(cipher.decrypt("not base64 at all"))
    }

    @Test fun `a different key can't read the token`() {
        val other = TokenCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertNull(other.decrypt(cipher.encrypt("tdc_secret")))
    }
}

class ErrorMessageTest {
    @Test fun `reads a plain error`() = assertEquals("Invalid credentials", errorMessageFrom("""{"error":"Invalid credentials"}"""))

    @Test fun `gives each field of a validation error with its messages`() =
        assertEquals("username: Required; password: Required, Too short", errorMessageFrom("""{"error":{"username":["Required"],"password":["Required","Too short"]}}"""))

    @Test fun `treats a validation error without fields as no reason`() = assertNull(errorMessageFrom("""{"error":{}}"""))

    @Test fun `ignores bodies that aren't the server's error shape`() {
        assertNull(errorMessageFrom("<html>Bad gateway</html>"))
        assertNull(errorMessageFrom("""{"message":"nope"}"""))
    }
}

class LocalNetworkTest {
    private fun local(ip: String) = isLocalNetwork(InetAddress.getByName(ip))

    @Test fun `private, link-local and unique local addresses are local`() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.0.12", "169.254.10.1", "fe80::1", "fd12:3456::1").forEach {
            assertTrue("$it should be local", local(it))
        }
    }

    @Test fun `public and loopback addresses are not`() {
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "127.0.0.1", "2001:4860:4860::8888", "::1").forEach {
            assertFalse("$it shouldn't be local", local(it))
        }
    }

    @Test fun `plain http may go to this phone and the local network only`() {
        listOf("127.0.0.1", "::1", "192.168.0.12", "10.0.2.2", "fe80::1").forEach {
            assertTrue("$it should allow http", allowsPlainHttp(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "100.64.0.1", "2001:4860:4860::8888").forEach {
            assertFalse("$it shouldn't allow http", allowsPlainHttp(InetAddress.getByName(it)))
        }
    }

    @Test fun `mDNS names count as local without a lookup`() =
        assertTrue(isOnLocalNetwork("http://conatus.local:3000/".toHttpUrl()))

    @Test fun `an IP address in the URL needs no DNS`() {
        assertTrue(isOnLocalNetwork("http://10.0.2.2:3000/".toHttpUrl()))
        assertFalse(isOnLocalNetwork("https://8.8.8.8/".toHttpUrl()))
    }
}
