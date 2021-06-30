package io.gnuf0rce.rss

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.FeedException
import com.rometools.rome.io.SyndFeedInput
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.*
import java.security.cert.X509Certificate
import javax.net.ssl.*

class RomeFeature internal constructor(val accept: List<ContentType>, val parser: () -> SyndFeedInput) {

    data class Config(
        var accept: MutableList<ContentType> = mutableListOf(
            ContentType.Text.Xml,
            ContentType.Application.Xml,
            ContentType.Application.Atom,
            ContentType.Application.Rss,
        ),
        var parser: () -> SyndFeedInput = { SyndFeedInput() }
    )

    constructor(config: Config) : this(config.accept, config.parser)

    companion object Feature : HttpClientFeature<Config, RomeFeature> {
        override val key: AttributeKey<RomeFeature> = AttributeKey("Rome")

        override fun prepare(block: Config.() -> Unit): RomeFeature = RomeFeature(Config().apply(block))

        override fun install(feature: RomeFeature, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                if (!info.type.java.isAssignableFrom(SyndFeed::class.java)) return@intercept

                if (!feature.accept.any { context.response.contentType()?.match(it) == true }) return@intercept

                val reader = body.toInputStream().reader(context.response.charset() ?: Charsets.UTF_8)
                val parsed = feature.parser().build(reader)
                proceedWith(HttpResponseContainer(info, parsed))
            }
        }
    }
}

open class RssHttpClient {
    protected open val ignore: (Throwable) -> Boolean = {
        when (it) {
            is ResponseException -> {
                false
            }
            is IOException,
            is HttpRequestTimeoutException,
            is FeedException -> {
                true
            }
            else -> {
                false
            }
        }
    }

    protected open val timeout: Long = 30 * 1000L

    protected open val parser: () -> SyndFeedInput = ::SyndFeedInput

    protected open val dns: Dns = DnsOverHttps("https://public.dns.iij.jp/dns-query")

    protected open val sni: List<Regex> = listOf("""sukebei\.nyaa\.(si|net)""".toRegex())

    protected open val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): MutableList<Proxy> = mutableListOf()

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    protected open fun client() = HttpClient(OkHttp) {
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = timeout
            socketTimeoutMillis = timeout
        }
        install(RomeFeature) {
            parser = this@RssHttpClient.parser
        }
        engine {
            config {
                sslSocketFactory(RubySSLSocketFactory(sni), RubyX509TrustManager)
                hostnameVerifier { _, _ -> true }
                proxySelector(proxySelector)
                dns(dns)
            }
        }
    }

    protected open val ignoreMax = 20

    suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = supervisorScope {
        var count = 0
        while (isActive) {
            runCatching {
                client().use { block(it) }
            }.onSuccess {
                return@supervisorScope it
            }.onFailure { throwable ->
                if (isActive && ignore(throwable)) {
                    count++
                    if (count > ignoreMax) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
        }
        throw CancellationException()
    }
}

fun DnsOverHttps(url: String): DnsOverHttps {
    return DnsOverHttps.Builder().apply {
        client(OkHttpClient())
        includeIPv6(false)
        url(url.toHttpUrl())
        post(true)
        resolvePrivateAddresses(false)
        resolvePublicAddresses(true)
    }.build()
}

fun Url.toProxy(): Proxy {
    val type = when (protocol) {
        URLProtocol.SOCKS -> Proxy.Type.SOCKS
        URLProtocol.HTTP -> Proxy.Type.HTTP
        else -> throw IllegalArgumentException("不支持的代理类型, $protocol")
    }
    val socket = if (host.matches("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""".toRegex())) {
        val adder = host.split('.').map { it.toByte() }.toByteArray()
        InetSocketAddress(InetAddress.getByAddress(adder), port)
    } else {
        InetSocketAddress(host, port)
    }
    return Proxy(type, socket)
}

open class RubySSLSocketFactory(protected open val sni: List<Regex>) : SSLSocketFactory() {

    private val predicate: (SNIServerName) -> Boolean = { name ->
        val host = name.encoded.toByteString().utf8()
        sni.none { it.matches(host) }
    }

    private fun Socket.setServerNames(): Socket = when (this) {
        is SSLSocket -> apply {
            // logger.info { inetAddress.hostAddress }
            sslParameters = sslParameters.apply {
                // cipherSuites = supportedCipherSuites
                protocols = supportedProtocols
                serverNames = serverNames.filter(predicate)
            }
        }
        else -> this
    }

    private val socketFactory: SSLSocketFactory = SSLContext.getDefault().socketFactory

    override fun createSocket(socket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket? =
        socketFactory.createSocket(socket, host, port, autoClose)?.setServerNames()

    override fun createSocket(host: String?, port: Int): Socket? =
        socketFactory.createSocket(host, port)?.setServerNames()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket? =
        socketFactory.createSocket(host, port, localHost, localPort)?.setServerNames()

    override fun createSocket(host: InetAddress?, port: Int): Socket? =
        socketFactory.createSocket(host, port)?.setServerNames()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket? =
        socketFactory.createSocket(address, port, localAddress, localPort)?.setServerNames()

    override fun getDefaultCipherSuites(): Array<String> = emptyArray()

    override fun getSupportedCipherSuites(): Array<String> = emptyArray()
}

private object RubyX509TrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}