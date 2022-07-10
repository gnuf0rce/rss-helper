package io.github.gnuf0rce.rss

import com.rometools.rome.io.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.*
import okhttp3.internal.*
import okhttp3.internal.tls.*
import java.net.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*


const val DefaultDnsOverHttps = "https://public.dns.iij.jp/dns-query"

val DefaultCNAME = mapOf(
    "twimg.com".toRegex() to listOf("twimg.twitter.map.fastly.net", "pbs.twimg.com.akamaized.net"),
)

val DefaultProxy = mapOf(
    "www.google.com" to "http://127.0.0.1:8080",
    "twitter.com" to "socks://127.0.0.1:1080"
)

val DefaultSNIHosts = listOf("""sukebei\.nyaa\.(si|net)""".toRegex())

const val DefaultTimeout = 30 * 1000L

interface RssHttpClientConfig {

    val doh get() = DefaultDnsOverHttps

    val ipv6 get() = false

    val cname get() = DefaultCNAME

    val proxy get() = DefaultProxy

    val sni get() = DefaultSNIHosts

    val timeout get() = DefaultTimeout
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

    protected open val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        ContentEncoding()
        install(HttpTimeout) {
            socketTimeoutMillis = timeout
            connectTimeoutMillis = timeout
            requestTimeoutMillis = null
        }
        install(RomePlugin) {
            parser = DefaultRomeParser
        }
        install(ContentNegotiation) {
            json(json = DefaultRssJson)
        }
        expectSuccess = true
        engine {
            config {
                // connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
                sslSocketFactory(RubySSLSocketFactory(sni), RubyX509TrustManager)
                hostnameVerifier { hostname, session ->
                    sni.any { it matches hostname } || OkHostnameVerifier.verify(hostname, session)
                }
                proxySelector(ProxySelector(this@RssHttpClient.proxy))
                dns(Dns(doh, cname, ipv6))
            }
        }
    }

    override val coroutineContext get() = client.coroutineContext

    override fun close() = client.close()

    protected open val max = 20

    suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = supervisorScope {
        var count = 0
        while (isActive) {
            try {
                return@supervisorScope block(client)
            } catch (throwable: Throwable) {
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

fun Dns(doh: String, cname: Map<Regex, List<String>>, ipv6: Boolean): okhttp3.Dns {
    val dns = (if (doh.isNotBlank()) DnsOverHttps(doh, ipv6) else okhttp3.Dns.SYSTEM)

    return object : okhttp3.Dns {

        private val lookup: (String) -> List<InetAddress> = {
            if (it.canParseAsIpAddress()) InetAddress.getAllByName(it).asList() else dns.lookup(it)
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val result = mutableListOf<InetAddress>()
            val other = cname.flatMap { (regex, list) -> if (regex in hostname) list else emptyList() }

            for (item in other) {
                try {
                    result.addAll(item.let(lookup))
                } catch (e: Throwable) {
                    //
                }
            }

            if (result.isEmpty()) {
                try {
                    result.addAll(hostname.let(lookup))
                } catch (e: Throwable) {
                    //
                }
            }

            return result.apply {
                if (isEmpty()) throw UnknownHostException("$hostname and CNAME${other} ")
                shuffle()
            }
        }
    }
}

fun DnsOverHttps(url: String, ipv6: Boolean): DnsOverHttps {
    return DnsOverHttps.Builder()
        .client(okhttp3.OkHttpClient())
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
        internal val logs = mutableMapOf<String, SSLParameters>()
    }

    private fun Socket.setServerNames(): Socket = apply {
        if (this !is SSLSocket) return@apply
        val address = inetAddress.hostAddress
        sslParameters = sslParameters.apply {
            serverNames = serverNames?.filter { name ->
                name !is SNIHostName || matcher.none { name.asciiName.matches(it) }
            }
        }
        logs[address] = sslParameters
        addHandshakeCompletedListener { logs.remove(address) }
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