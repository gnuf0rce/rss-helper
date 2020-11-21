package io.gnuf0rce.mirai.plugin.tools

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.request.*
import java.io.InputStream

object Feed {

    private suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = HttpClient(OkHttp) {
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            requestTimeoutMillis = 180_000
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
    }.use { block(it) }

    suspend fun feedUrl(url: String) = useHttpClient { client ->
        SyndFeedInput().build(XmlReader(client.get<InputStream>(url)))
    }
}