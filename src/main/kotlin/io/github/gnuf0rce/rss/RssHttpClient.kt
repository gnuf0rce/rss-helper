package io.github.gnuf0rce.rss

import com.rometools.rome.io.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import okhttp3.internal.tls.*
import kotlin.coroutines.*

public open class RssHttpClient : CoroutineScope, Closeable, RssHttpClientConfig {
    protected open val ignore: (Throwable) -> Boolean = {
        when (it) {
            is ResponseException -> {
                false
            }
            is IOException,
            is FeedException -> {
                true
            }
            else -> {
                false
            }
        }
    }

    protected open val client: HttpClient = HttpClient(OkHttp) {
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

    override val coroutineContext: CoroutineContext get() = client.coroutineContext

    override fun close(): Unit = client.close()

    protected open val max: Int = 20

    public suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = supervisorScope {
        var count = 0
        var current: Throwable? = null
        while (isActive) {
            try {
                return@supervisorScope block(client)
            } catch (cause: Throwable) {
                current = cause
                if (isActive && ignore(cause)) {
                    if (++count > max) {
                        throw cause
                    }
                } else {
                    throw cause
                }
            }
        }
        throw CancellationException(null, current)
    }
}