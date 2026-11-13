package com.linkinlt.tubelite

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** Kết nối mạng. */
object DownloaderImpl : Downloader() {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"

    val client: OkHttpClient by lazy {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        // Android 5 thiếu chứng chỉ gốc mới (GitHub, Let's Encrypt...): thêm vào
        try {
            val tm = extraTrust()
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), null)
            b.sslSocketFactory(ctx.socketFactory, tm)
        } catch (_: Exception) {}
        b.build()
    }

    private fun extraTrust(): javax.net.ssl.X509TrustManager {
        val algo = javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        val sysF = javax.net.ssl.TrustManagerFactory.getInstance(algo)
        sysF.init(null as java.security.KeyStore?)
        val sys = sysF.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
        val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply { load(null, null) }
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        App.instance.resources.openRawResource(R.raw.extra_roots).use { input ->
            cf.generateCertificates(input).forEachIndexed { i, c -> ks.setCertificateEntry("x$i", c) }
        }
        val exF = javax.net.ssl.TrustManagerFactory.getInstance(algo)
        exF.init(ks)
        val extra = exF.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
        return object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) =
                sys.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                try { sys.checkServerTrusted(chain, authType) } catch (e: Exception) { extra.checkServerTrusted(chain, authType) }
            }
            override fun getAcceptedIssuers() = sys.acceptedIssuers + extra.acceptedIssuers
        }
    }

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val b = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)
        if (request.url().contains("youtube.com")) b.addHeader("Cookie", "CONSENT=YES+")
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { b.addHeader(name, it) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = r.body?.string()
            return Response(r.code, r.message, r.headers.toMultimap(), text, r.request.url.toString())
        }
    }
}
