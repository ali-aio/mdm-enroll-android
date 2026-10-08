package com.aioapp.enroll.adb

import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.cert.X509Certificate
import java.util.Date

/**
 * An adb identity: the RSA key adbd checks (plain adb) and the self-signed certificate made from it
 * (adb's TLS, used by Wireless debugging). adb only looks at the key; the certificate just carries it.
 */
class AdbIdentity(val label: String, val privateKey: PrivateKey) {
    val certificate: X509Certificate by lazy { selfSigned(privateKey) }

    fun pkcs8Base64(): String = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)

    companion object {
        /** A new key for this phone; it becomes trusted by pairing (a code, or Allow on the device). */
        fun generate(): AdbIdentity {
            val g = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            return AdbIdentity("this phone", g.generateKeyPair().private)
        }

        fun fromPkcs8Base64(label: String, b64: String): AdbIdentity =
            AdbIdentity(label, KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(b64, Base64.NO_WRAP))))

        /** adb's `adbkey` file: PEM, PKCS#8 ("PRIVATE KEY") or PKCS#1 ("RSA PRIVATE KEY"). */
        fun fromPem(label: String, pem: String): AdbIdentity {
            val conv = JcaPEMKeyConverter()
            val key = when (val o = PEMParser(StringReader(pem)).readObject()) {
                is PEMKeyPair -> conv.getKeyPair(o).private
                is PrivateKeyInfo -> conv.getPrivateKey(o)
                else -> throw IllegalArgumentException("not an RSA private key")
            }
            return AdbIdentity(label, key)
        }

        private fun publicOf(k: PrivateKey): PublicKey {
            val rsa = k as RSAPrivateCrtKey
            return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(rsa.modulus, rsa.publicExponent))
        }

        private fun selfSigned(k: PrivateKey): X509Certificate {
            val name = X500Name("CN=AIO Enroll")
            val now = System.currentTimeMillis()
            val b = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 10L * 365 * 86_400_000L), name, publicOf(k))
            return JcaX509CertificateConverter().getCertificate(b.build(JcaContentSignerBuilder("SHA256withRSA").build(k)))
        }
    }
}
