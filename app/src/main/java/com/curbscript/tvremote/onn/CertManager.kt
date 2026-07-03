package com.curbscript.tvremote.onn

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Generates and persists a self-signed RSA client certificate used to pair with,
 * and authenticate to, the Android TV Remote service on the onn 4K Pro.
 *
 * The same certificate must be reused for pairing (port 6467) and for sending
 * commands (port 6466) — the TV authorizes this specific certificate during
 * pairing, so it is stored once in a PKCS12 keystore in app-private storage.
 */
class CertManager(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "atv_client.p12")
    private val password = "curbremote".toCharArray()
    private val alias = "atv"

    @Synchronized
    fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (storeFile.exists()) {
            storeFile.inputStream().use { ks.load(it, password) }
            if (ks.containsAlias(alias)) return ks
        } else {
            ks.load(null, null)
        }
        generateInto(ks)
        storeFile.outputStream().use { ks.store(it, password) }
        return ks
    }

    private fun generateInto(ks: KeyStore) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)
        val notAfter = Date(now + 3650L * 24 * 60 * 60 * 1000)
        val dn = X500Name("CN=atvremote, O=Google Inc., OU=Android, C=US")
        val builder = JcaX509v3CertificateBuilder(
            dn, BigInteger.valueOf(now), notBefore, notAfter, dn, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(kp.private)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(builder.build(signer))
        ks.setKeyEntry(alias, kp.private, password, arrayOf(cert))
    }

    fun clientPublicKey(): RSAPublicKey =
        (keyStore().getCertificate(alias) as X509Certificate).publicKey as RSAPublicKey

    /** SSL factory that presents our client certificate and trusts any server cert. */
    fun socketFactory(): SSLSocketFactory {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(), password)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(TrustAll), SecureRandom())
        return ctx.socketFactory
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
