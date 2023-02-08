package io.github.gnuf0rce.mirai.rss

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.ParsingFeedException
import io.github.gnuf0rce.mirai.rss.data.HttpClientConfig
import io.github.gnuf0rce.rss.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.warning
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import javax.net.ssl.SSLException

internal val logger by lazy {
    try {
        RssHelperPlugin.logger
    } catch (_: ExceptionInInitializerError) {
        MiraiLogger.Factory.create(RssHttpClient::class)
    }
}

internal val ImageFolder get() = RssHelperPlugin.dataFolder.resolve("image")

internal val TorrentFolder get() = RssHelperPlugin.dataFolder.resolve("torrent")

internal val client: RssHttpClient by lazy {
    object : RssHttpClient(), RssHttpClientConfig by HttpClientConfig {
        override val ignore: (Throwable) -> Boolean = { cause ->
            when (cause) {
                is UnknownHostException -> false
                is SSLException -> {
                    val message = cause.message.orEmpty()
                    for ((address, ssl) in RubySSLSocketFactory.logs) {
                        if (address in message) {
                            File("./rss_ssl_error.${address}.log").appendText(buildString {
                                appendLine("$address ${OffsetDateTime.now()} $message")
                                appendLine("protocols: ${ssl.protocols.asList()}")
                                appendLine("cipherSuites: ${ssl.cipherSuites.asList()}")
                                appendLine("serverNames: ${ssl.serverNames}")
                            })
                        }
                    }
                    false
                }
                is IOException -> {
                    if (cause.message == "Connection reset") {
                        false
                    } else {
                        logger.warning({ "RssHttpClient IOException" }, cause)
                        true
                    }
                }
                is ParsingFeedException -> {
                    logger.warning({ "RssHttpClient XML解析失败" }, cause)
                    false
                }
                else -> false
            }
        }
        override val timeout: Long get() = HttpClientConfig.timeout
    }
}

@PublishedApi
internal val Url.filename: String get() = encodedPath.substringAfterLast('/').decodeURLPart()

@PublishedApi
internal fun HttpMessage.contentDisposition(): ContentDisposition? {
    return ContentDisposition.parse(headers[HttpHeaders.ContentDisposition] ?: return null)
}

@PublishedApi
internal fun MessageChainBuilder.appendKeyValue(key: String, value: Any?) {
    when (value) {
        null, Unit -> Unit
        is String -> {
            if (value.isNotBlank()) appendLine("$key: $value")
        }

        is Collection<*> -> {
            if (value.isNotEmpty()) appendLine("$key: $value")
        }

        else -> appendLine("$key: $value")
    }
}

internal fun String.toDecodedURL(): String {
    return URLDecoder.decode(this, StandardCharsets.UTF_8)
}

@PublishedApi
internal suspend fun SyndEntry.toMessage(subject: Contact, limit: Int, forward: Boolean): Message {
    val head = buildMessageChain {
        appendKeyValue("标题", title.toDecodedURL())
        appendKeyValue("链接", link)
        appendKeyValue("发布时间", published.toReadable())
        appendKeyValue("更新时间", updated.takeIf { it != published }?.toReadable())
        appendKeyValue("分类", categories.map { it.name })
        appendKeyValue("作者", author)
        appendKeyValue("种子", torrent)
    }

    val message = html?.toMessage(subject) ?: text.orEmpty().toPlainText()

    return if (forward) {
        val second = last.orNow().toEpochSecond().toInt()
        buildForwardMessage(subject) {
            subject.bot at second says head
            subject.bot at second says message

            displayStrategy = toDisplayStrategy()
        }
    } else {
        head + if (message.content.length <= limit) message else "内容过长".toPlainText()
    }
}

@PublishedApi
internal fun SyndEntry.toDisplayStrategy(): ForwardMessage.DisplayStrategy = object : ForwardMessage.DisplayStrategy {
    override fun generatePreview(forward: RawForwardMessage): List<String> {
        return listOf(
            title,
            author,
            last.toReadable(),
            categories.joinToString { it.name }
        )
    }
}

private val FULLWIDTH_CHARS = mapOf(
    '\\' to '＼',
    '/' to '／',
    ':' to '：',
    '*' to '＊',
    '?' to '？',
    '"' to '＂',
    '<' to '＜',
    '>' to '＞',
    '|' to '｜'
)

private fun CharSequence.fullwidth(): String {
    return fold(StringBuilder()) { buffer, char ->
        buffer.append(FULLWIDTH_CHARS[char] ?: char)
    }.toString()
}

@PublishedApi
internal suspend fun SyndEntry.getTorrent(): File? {
    // TODO magnet to file
    val url = Url(torrent?.takeIf { it.startsWith("http") } ?: return null)
    return try {
        TorrentFolder.resolve("${title.fullwidth()}.torrent").apply {
            if (exists().not()) {
                parentFile.mkdirs()
                writeBytes(client.useHttpClient { it.get(url).body() })
            }
        }
    } catch (cause: Exception) {
        logger.warning({ "下载种子失败" }, cause)
        null
    }
}

@PublishedApi
internal fun Element.src(): String = attr("src") ?: throw NoSuchElementException("src")

@PublishedApi
internal fun Element.href(): String = attr("href") ?: throw NoSuchElementException("href")

@PublishedApi
internal suspend fun Element.image(subject: Contact): MessageContent {
    val url = Url(src())
    return try {
        val image = if (ImageFolder.resolve(url.filename).exists()) {
            ImageFolder.resolve(url.filename)
        } else {
            client.useHttpClient { http ->
                val response = http.get(url)
                val relative = response.contentDisposition()?.parameter(ContentDisposition.Parameters.FileName)
                    ?: response.etag()?.removeSurrounding("\"")
                        ?.plus(".")?.plus(response.contentType()?.contentSubtype)
                    ?: response.request.url.filename

                val file = ImageFolder.resolve(relative)

                if (file.exists().not()) {
                    file.parentFile.mkdirs()
                    file.outputStream().use { output ->
                        val channel: ByteReadChannel = response.bodyAsChannel()

                        while (!channel.isClosedForRead) {
                            channel.copyTo(output)
                        }
                    }
                }

                file
            }
        }
        image.uploadAsImage(subject)
    } catch (cause: Exception) {
        logger.warning({ "上传图片失败, $url" }, cause)
        " [$url] ".toPlainText()
    }
}

@PublishedApi
internal suspend fun Element.toMessage(subject: Contact): MessageChain {
    val visitor = object : NodeVisitor, MutableList<Node> by ArrayList() {
        override fun head(node: Node, depth: Int) {
            if (node is TextNode) add(node)
        }

        override fun tail(node: Node, depth: Int) {
            if (node is Element) add(node)
        }
    }
    NodeTraversor.traverse(visitor, this)

    val builder = MessageChainBuilder()
    visitor.forEach { node ->
        when (node) {
            is TextNode -> builder.append(node.text())
            is Element -> when (node.nodeName()) {
                "img" -> {
                    builder.append(node.image(subject))
                }
                "a" -> {
                    when {
                        node.text() == node.href() -> Unit
                        node.childrenSize() > 0 -> Unit
                        else -> builder.append("<${node.href()}>")
                    }
                }
                "br" -> {
                    builder.append("\n")
                }
            }
            else -> error(node)
        }
    }

    return builder.build()
}