package io.github.gnuf0rce.rss

import com.rometools.rome.io.*
import io.ktor.http.*
import io.ktor.utils.io.errors.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Dns
import okhttp3.dnsoverhttps.*
import okhttp3.internal.*
import java.net.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

@PublishedApi
internal const val DefaultDnsOverHttps: String = "https://public.dns.iij.jp/dns-query"

@PublishedApi
internal val DefaultCNAME: Map<Regex, List<String>> = mapOf(
    "twimg.com".toRegex() to listOf("twimg.twitter.map.fastly.net", "pbs.twimg.com.akamaized.net"),
)

@PublishedApi
internal val DefaultProxy: Map<String, String> = mapOf(
    "www.google.com" to "http://127.0.0.1:8080",
    "twitter.com" to "socks://127.0.0.1:1080"
)

@PublishedApi
internal val DefaultSNIHosts: List<Regex> = listOf("""sukebei\.nyaa\.(si|net)""".toRegex())

@PublishedApi
internal const val DefaultTimeout: Long = 30 * 1000L

public interface RssHttpClientConfig {

    public val doh: String get() = DefaultDnsOverHttps

    public val ipv6: Boolean get() = false

    public val cname: Map<Regex, List<String>> get() = DefaultCNAME

    public val proxy: Map<String, String> get() = DefaultProxy

    public val sni: List<Regex> get() = DefaultSNIHosts

    public val timeout: Long get() = DefaultTimeout
}

@PublishedApi
internal val DefaultRssJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

@PublishedApi
internal val DefaultRomeParser: () -> SyndFeedInput = ::SyndFeedInput

@PublishedApi
internal fun ProxySelector(proxy: Map<String, String>): ProxySelector = object : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        return buildList {
            for ((host, url) in proxy) {
                if (uri?.host == host || host == "127.0.0.1" || host == "localhost" || host == "default") {
                    add(Url(url).toProxy())
                }
            }
            ifEmpty {
                val system = System.getProperty("java.net.useSystemProxies", "false")
                try {
                    System.setProperty("java.net.useSystemProxies", "true")
                    addAll(getDefault().select(uri))
                } finally {
                    System.setProperty("java.net.useSystemProxies", system)
                }
            }
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

@PublishedApi
internal fun Dns(doh: String, cname: Map<Regex, List<String>>, ipv6: Boolean): Dns = object : Dns {
    private val dns = if (doh.isNotEmpty()) DnsOverHttps(doh, ipv6) else null

    override fun lookup(hostname: String): List<InetAddress> = buildList {
        val other = cname.flatMap { (regex, list) -> if (regex in hostname) list else emptyList() }
        val cache = ArrayList<Exception>()

        for (item in other) {
            try {
                val result = if (item.canParseAsIpAddress()) {
                    Dns.SYSTEM.lookup(item)
                } else {
                    (dns ?: Dns.SYSTEM).lookup(item)
                }
                addAll(result)
            } catch (cause: Exception) {
                cache.add(cause)
            }
        }

        if (isEmpty()) {
            try {
                val result = if (hostname.canParseAsIpAddress()) {
                    Dns.SYSTEM.lookup(hostname)
                } else {
                    (dns ?: Dns.SYSTEM).lookup(hostname)
                }
                addAll(result)
            } catch (cause: Exception) {
                cache.add(cause)
            }
        }

        if (isEmpty() && cache.isEmpty().not()) {
            try {
                addAll(Dns.SYSTEM.lookup(hostname))
            } catch (cause: Exception) {
                cache.add(cause)
            }
        }

        if (isEmpty()) {
            val exception = UnknownHostException("$hostname and CNAME${other} by DoH(url=${doh})")
            for (suppressed in cache) {
                exception.addSuppressed(suppressed)
            }
            throw exception
        }
        shuffle()
    }
}

@PublishedApi
internal fun DnsOverHttps(url: String, ipv6: Boolean): DnsOverHttps {
    return DnsOverHttps.Builder()
        .client(okhttp3.OkHttpClient())
        .url(url.toHttpUrl())
        .post(true)
        .includeIPv6(ipv6)
        .resolvePrivateAddresses(false)
        .resolvePublicAddresses(true)
        .build()
}

@PublishedApi
internal fun Url.toProxy(): Proxy {
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

@PublishedApi
internal class RubySSLSocketFactory(private val matcher: List<Regex>) : SSLSocketFactory() {
    companion object {
        private val default: SSLSocketFactory = SSLContext(tm = RubyX509TrustManager).socketFactory
        internal val logs = mutableMapOf<String, SSLParameters>()
    }

    private fun Socket.setServerNames(): Socket = apply {
        if (this !is SSLSocket) return@apply
        val address = inetAddress?.hostAddress ?: return@apply
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

@PublishedApi
internal object RubyX509TrustManager : X509TrustManager by X509TrustManager() {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}