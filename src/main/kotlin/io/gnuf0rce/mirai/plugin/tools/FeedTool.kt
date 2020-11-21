package io.gnuf0rce.mirai.plugin.tools

import com.rometools.rome.io.SyndFeedInput
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FeedTool {

    private suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = HttpClient(OkHttp) {
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
    }.use { block(it) }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun parseFeedXml(timestamp: Long, dir: File) = withContext(Dispatchers.IO) {
        SyndFeedInput().build(dir.resolve("${timestamp}.xml"))
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getFeedXml(url: String, dir: File): Long =
        useHttpClient { client -> client.get<ByteArray>(url) }.let { bytes ->
            dir.resolve("temp.xml").run {
                writeBytes(bytes)
                SyndFeedInput().build(this).publishedDate.toInstant().epochSecond.also { timestamp ->
                    this.renameTo(dir.resolve("${timestamp}.xml"))
                }
            }
        }
}