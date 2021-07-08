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
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.internal.canParseAsIpAddress
import java.io.IOException
import java.net.*
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.coroutines.CoroutineContext

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

const val DefaultDnsOverHttps = "https://public.dns.iij.jp/dns-query"

val DefaultCNAME = mapOf(
    "twimg.com".toRegex() to listOf("twimg.twitter.map.fastly.net", "pbs.twimg.com.akamaized.net"),
    "github.com".toRegex() to listOf("13.229.188.59")
)

val DefaultProxy = mapOf(
    "www.google.com" to "http://127.0.0.1:8080",
    "twitter.com" to "socks://127.0.0.1:1080"
)

val DefaultSNIHosts: Set<String> = setOf("""sukebei\.nyaa\.(si|net)""")

interface RssHttpClientConfig {

    val doh get() = DefaultDnsOverHttps

    val cname get() = DefaultCNAME

    val proxy get() = DefaultProxy

    val sni: Set<String> get() = DefaultSNIHosts
}

open class RssHttpClient : CoroutineScope, Closeable, RssHttpClientConfig {
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

    protected open val client = HttpClient(OkHttp) {
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
                proxySelector(ProxySelector(this@RssHttpClient.proxy))
                dns(Dns(doh, cname))
            }
        }
    }

    override val coroutineContext: CoroutineContext get() = client.coroutineContext

    override fun close() = client.close()

    protected open val ignoreMax = 20

    suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = supervisorScope {
        var count = 0
        while (isActive) {
            runCatching {
                block(client)
            }.onSuccess {
                return@supervisorScope it
            }.onFailure { throwable ->
                if (isActive && ignore(throwable)) {
                    if (++count > ignoreMax) {
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

fun ProxySelector(proxy: Map<String, String>): ProxySelector = object : ProxySelector() {
    override fun select(uri: URI?): MutableList<Proxy> {
        return proxy.mapNotNull { (host, url) ->
            if (uri?.host.orEmpty() == host || host == "127.0.0.1") {
                Url(url).toProxy()
            } else {
                null
            }
        }.toMutableList()
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

fun Dns(doh: String, cname: Map<Regex, List<String>>): Dns {
    val dns = (if (doh.isNotBlank()) DnsOverHttps(doh) else Dns.SYSTEM)

    return object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lookup: (String) -> List<InetAddress> = {
                if (hostname.canParseAsIpAddress()) InetAddress.getAllByName(it).asList() else dns.lookup(it)
            }
            if (hostname.canParseAsIpAddress()) return InetAddress.getAllByName(hostname).asList()
            val result = mutableListOf<InetAddress>()
            val other = cname.flatMap { (regex, list) -> if (regex in hostname) list else emptyList() }

            other.forEach {
                runCatching {
                    result.addAll(lookup(it))
                }
            }

            runCatching {
                result.addAll(lookup(hostname))
            }

            return result.apply {
                if (isEmpty()) throw UnknownHostException("$hostname and CNAME${other} ")
            }
        }
    }
}

fun DnsOverHttps(url: String, sni: Boolean = true): DnsOverHttps {
    return DnsOverHttps.Builder().apply {
        client(OkHttpClient.Builder().apply {
            if (sni) {
                sslSocketFactory(RubySSLSocketFactory(listOf(url.toHttpUrl().host.toRegex())), RubyX509TrustManager)
                hostnameVerifier { _, _ -> true }
            }
        }.build())
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
    return Proxy(type, InetSocketAddress(host, port))
}

class RubySSLSocketFactory(private val regexes: List<Regex>) : SSLSocketFactory() {
    constructor(sni: Set<String>) : this(regexes = sni.map { it.toRegex() })

    private val predicate: (SNIHostName) -> Boolean = { name ->
        regexes.none { it.matches(name.asciiName) }
    }

    private fun Socket.setServerNames(): Socket = when (this) {
        is SSLSocket -> apply {
            // logger.info { inetAddress.hostAddress }
            sslParameters = sslParameters.apply {
                // cipherSuites = supportedCipherSuites
                // protocols = supportedProtocols
                serverNames = serverNames.filterIsInstance<SNIHostName>().filter(predicate)
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