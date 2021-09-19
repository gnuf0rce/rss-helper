package io.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.*
import okhttp3.internal.*
import okhttp3.internal.tls.*
import java.net.*
import java.security.*
import java.security.cert.*
import java.util.concurrent.*
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
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.accept.forEach { context.accept(it) }
            }

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

val DefaultSNIHosts = listOf("""sukebei\.nyaa\.(si|net)""".toRegex())

interface RssHttpClientConfig {

    val doh get() = DefaultDnsOverHttps

    val ipv6 get() = false

    val cname get() = DefaultCNAME

    val proxy get() = DefaultProxy

    val sni get() = DefaultSNIHosts
}

val DefaultRssJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

val DefaultRomeParser: () -> SyndFeedInput = ::SyndFeedInput

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

    protected open val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        ContentEncoding()
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = timeout
            socketTimeoutMillis = timeout
        }
        install(RomeFeature) {
            parser = DefaultRomeParser
        }
        Json {
            serializer = KotlinxSerializer(DefaultRssJson)
        }
        engine {
            config {
                connectionPool(ConnectionPool(6, 15, TimeUnit.MINUTES))
                sslSocketFactory(RubySSLSocketFactory(sni), RubyX509TrustManager)
                hostnameVerifier { hostname, session ->
                    sni.any { it in hostname } || OkHostnameVerifier.verify(hostname, session)
                }
                proxySelector(ProxySelector(this@RssHttpClient.proxy))
                dns(Dns(doh, cname, ipv6))
            }
        }
    }

    override val coroutineContext get() = client.coroutineContext

    override fun close() = client.close()

    protected open val max = 20

    protected val engine get() = (client.engineConfig as OkHttpConfig)

    suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = supervisorScope {
        var count = 0
        while (isActive) {
            runCatching {
                block(client)
            }.onSuccess {
                return@supervisorScope it
            }.onFailure { throwable ->
                if (isActive && ignore(throwable)) {
                    if (++count > max) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
        }
        throw CancellationException(null, null)
    }
}

fun ProxySelector(proxy: Map<String, String>): ProxySelector = object : ProxySelector() {
    override fun select(uri: URI?): MutableList<Proxy> {
        return proxy.mapNotNull { (host, url) ->
            if (uri?.host == host || host == "127.0.0.1") {
                Url(url).toProxy()
            } else {
                null
            }
        }.toMutableList()
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

fun Dns(doh: String, cname: Map<Regex, List<String>>, ipv6: Boolean): Dns {
    val dns = (if (doh.isNotBlank()) DnsOverHttps(doh, ipv6) else Dns.SYSTEM)

    return object : Dns {

        private val lookup: (String) -> List<InetAddress> = {
            if (it.canParseAsIpAddress()) InetAddress.getAllByName(it).asList() else dns.lookup(it)
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val result = mutableListOf<InetAddress>()
            val other = cname.flatMap { (regex, list) -> if (regex in hostname) list else emptyList() }

            other.forEach {
                runCatching {
                    result.addAll(it.let(lookup))
                }
            }

            result.shuffle()

            if (result.isEmpty()) runCatching {
                result.addAll(hostname.let(lookup))
            }

            if (result.isEmpty()) runCatching {
                result.addAll(InetAddress.getAllByName(hostname))
            }

            return result.apply {
                if (isEmpty()) throw UnknownHostException("$hostname and CNAME${other} ")
            }
        }
    }
}

fun DnsOverHttps(url: String, ipv6: Boolean): DnsOverHttps {
    return DnsOverHttps.Builder()
        .client(OkHttpClient())
        .url(url.toHttpUrl())
        .post(true)
        .includeIPv6(ipv6)
        .resolvePrivateAddresses(false)
        .resolvePublicAddresses(true)
        .build()
}

fun Url.toProxy(): Proxy {
    val type = when (protocol) {
        URLProtocol.SOCKS -> Proxy.Type.SOCKS
        URLProtocol.HTTP -> Proxy.Type.HTTP
        else -> throw IllegalArgumentException("不支持的代理类型, $protocol")
    }
    return Proxy(type, InetSocketAddress(host, port))
}

private fun X509TrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

private fun SSLContext(tm: X509TrustManager = X509TrustManager()): SSLContext {
    return SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(tm), null)
    }
}

class RubySSLSocketFactory(private val matcher: List<Regex>) : SSLSocketFactory() {
    companion object {
        private val default: SSLSocketFactory = SSLContext(tm = RubyX509TrustManager).socketFactory
    }

    private fun Socket.setServerNames(): Socket = apply {
        if (this !is SSLSocket) return@apply
        sslParameters = sslParameters.apply {
            serverNames = serverNames?.filter { name ->
                if (name !is SNIHostName) return@filter true
                matcher.none { name.asciiName.matches(it) }
            }
        }
    }

    override fun createSocket(socket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket? =
        default.createSocket(socket, host, port, autoClose)?.setServerNames()

    override fun createSocket(host: String?, port: Int): Socket? =
        default.createSocket(host, port)?.setServerNames()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket? =
        default.createSocket(host, port, localHost, localPort)?.setServerNames()

    override fun createSocket(host: InetAddress?, port: Int): Socket? =
        default.createSocket(host, port)?.setServerNames()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket? =
        default.createSocket(address, port, localAddress, localPort)?.setServerNames()

    override fun getDefaultCipherSuites(): Array<String> = default.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = default.supportedCipherSuites
}

object RubyX509TrustManager : X509TrustManager by X509TrustManager() {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    // override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}