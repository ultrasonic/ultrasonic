package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should throw`
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory

private const val PORT = 8443
private const val HOST = "localhost"

/**
 * Integration test to check [SubsonicAPIClient] interaction with different SSL scenarios.
 */
class SubsonicApiSSLTest {
    private val mockWebServer = MockWebServer()

    @Before
    fun setUp() {
        val sslContext = createSSLContext(loadResourceStream("self-signed.pem"),
                loadResourceStream("self-signed.p12"), "")
        mockWebServer.useHttps(sslContext.socketFactory, false)
        mockWebServer.start(InetAddress.getByName(HOST), PORT)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createSSLContext(
        certificatePemStream: InputStream,
        certificatePkcs12Stream: InputStream,
        password: String
    ): SSLContext {
        var cert: X509Certificate? = null
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustStore.load(null)

        certificatePemStream.use {
            cert = (CertificateFactory.getInstance("X.509")
                    .generateCertificate(certificatePemStream)) as X509Certificate
        }
        val alias = cert?.subjectX500Principal?.name
                ?: throw IllegalStateException("Failed to load certificate")
        trustStore.setCertificateEntry(alias, cert)

        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(trustStore)
        val trustManagers = tmf.trustManagers
        val sslContext = SSLContext.getInstance("TLS")

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(certificatePkcs12Stream, password.toCharArray())
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, password.toCharArray())

        sslContext.init(kmf.keyManagers, trustManagers, null)
        return sslContext
    }

    @Test
    fun `Should fail request if self-signed certificate support is disabled`() {
        val client = createSubsonicClient(false)
        mockWebServer.enqueueResponse("ping_ok.json")

        val fail = {
            client.api.ping().execute()
        }

        fail `should throw` SSLHandshakeException::class
    }

    @Test
    fun `Should pass request if self-signed certificate support is enabled`() {
        val client = createSubsonicClient(true)
        mockWebServer.enqueueResponse("ping_ok.json")

        val response = client.api.ping().execute()

        assertResponseSuccessful(response)
    }

    private fun createSubsonicClient(allowSelfSignedCertificate: Boolean): SubsonicAPIClient {
        val config = SubsonicClientConfiguration(
            "https://$HOST:$PORT/",
            USERNAME,
            PASSWORD,
            CLIENT_VERSION,
            CLIENT_ID,
            allowSelfSignedCertificate = allowSelfSignedCertificate
        )
        return SubsonicAPIClient(config)
    }
}
