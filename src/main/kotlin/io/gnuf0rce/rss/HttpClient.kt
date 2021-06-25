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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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

private val Ignore: (Throwable) -> Boolean = {
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

private val DefaultSyndFeedInput: SyndFeedInput = SyndFeedInput()

var DnsOverHttpsUrl = "https://public.dns.iij.jp/dns-query"

private fun DnsOverHttps(url: String): DnsOverHttps {
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
    val socket = if (hostIsIp(host)) {
        val addr = host.split('.').map { it.toByte() }.toByteArray()
        InetSocketAddress(InetAddress.getByAddress(addr), port)
    } else {
        InetSocketAddress(host, port)
    }
    return Proxy(type, socket)
}

var ProxySelect: MutableList<Proxy>.(uri: URI?) -> Unit = { }

var ProxyConnectFailed: (uri: URI?, sa: SocketAddress?, ioe: IOException?) -> Unit = { _, _, _ -> }

private object RssProxySelector : ProxySelector() {

    override fun select(uri: URI?): MutableList<Proxy> = mutableListOf<Proxy>().apply { ProxySelect(uri) }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = ProxyConnectFailed(uri, sa, ioe)
}

var ClientTimeout = 30 * 1000L

private fun client() = HttpClient(OkHttp) {
    BrowserUserAgent()
    ContentEncoding {
        gzip()
        deflate()
        identity()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = ClientTimeout
        connectTimeoutMillis = ClientTimeout
        socketTimeoutMillis = ClientTimeout
    }
    install(RomeFeature) {
        parser = { DefaultSyndFeedInput }
    }
    engine {
        config {
            sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
            hostnameVerifier { _, _ -> true }
            // proxySelector(RssProxySelector)
            dns(DnsOverHttps(DnsOverHttpsUrl))
        }
    }
}

internal suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = withContext(Dispatchers.IO) {
    while (isActive) {
        runCatching {
            client().use { block(it) }
        }.onSuccess {
            return@withContext it
        }.onFailure { throwable ->
            if (isActive && Ignore(throwable)) {
                // logger.warning(throwable::toString)
            } else {
                throw throwable
            }
        }
    }
    throw CancellationException()
}

internal val HostNames = listOf("""sukebei\.nyaa\.(si|net)""".toRegex())

var SNIServerNamePredicate: (SNIServerName) -> Boolean = { name ->
    val host = name.encoded.toByteString().utf8()
    // logger.info { host }
    HostNames.none { it.matches(host) }
}

private object RubySSLSocketFactory : SSLSocketFactory() {

    private fun Socket.setServerNames(): Socket = when (this) {
        is SSLSocket -> apply {
            // logger.info { inetAddress.hostAddress }
            sslParameters = sslParameters.apply {
                // cipherSuites = supportedCipherSuites
                protocols = supportedProtocols
                serverNames = serverNames.filter(SNIServerNamePredicate)
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