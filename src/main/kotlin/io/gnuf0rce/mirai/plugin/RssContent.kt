package io.gnuf0rce.mirai.plugin

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.*
import io.gnuf0rce.mirai.plugin.data.*
import io.gnuf0rce.rss.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.RemoteFile.Companion.uploadFile
import net.mamoe.mirai.utils.*
import org.jsoup.nodes.*
import org.jsoup.select.*
import java.io.*

internal val logger by RssHelperPlugin::logger

internal val ImageFolder get() = RssHelperPlugin.dataFolder.resolve("image")

internal val TorrentFolder get() = RssHelperPlugin.dataFolder.resolve("torrent")

internal val client: RssHttpClient by lazy {
    object : RssHttpClient(), RssHttpClientConfig by HttpClientConfig {
        override val ignore: (Throwable) -> Boolean = {
            when (it) {
                is IOException,
                is HttpRequestTimeoutException -> {
                    val message = it.message.orEmpty()
                    when {
                        "Connection reset" in message -> {
                            logger.warning { "RssHttpClient Ignore 链接被重置，可能需要添加SNI过滤 $it" }
                        }
                        it is ParsingFeedException -> {
                            logger.warning { "RssHttpClient Ignore XML解析失败 $it" }
                        }
                        else -> {
                            logger.warning { "RssHttpClient Ignore $it" }
                        }
                    }
                    true
                }
                else -> {
                    false
                }
            }
        }
    }
}

private val Url.filename get() = encodedPath.substringAfterLast('/')

fun MessageChainBuilder.appendKeyValue(key: String, value: Any?) {
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

fun SyndEntry.toMessage(subject: Contact? = null, limit: Int = RssContentConfig.limit) = buildMessageChain {
    appendKeyValue("标题", title)
    appendKeyValue("链接", link)
    appendKeyValue("发布时间", published)
    appendKeyValue("更新时间", updated.takeIf { it != published })
    appendKeyValue("分类", categories.map { it.name })
    appendKeyValue("作者", author)
    appendKeyValue("种子", torrent)
    (html?.toMessage(subject) ?: text.orEmpty().toPlainText()).let {
        if (it.content.length <= limit) {
            add(it)
        } else {
            appendLine("内容过长")
        }
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

private fun String.toFullWidth(): String = fold("") { acc, char -> acc + (FULLWIDTH_CHARS[char] ?: char) }

suspend fun SyndEntry.toTorrent(subject: FileSupported): Message? {
    // TODO magnet to file
    val url = Url(torrent?.takeIf { it.startsWith("http") } ?: return null)
    return runCatching {
        TorrentFolder.resolve("${title.toFullWidth()}.torrent").apply {
            if (exists().not()) {
                parentFile.mkdirs()
                writeBytes(client.useHttpClient { it.get(url) })
            }
        }
    }.onFailure {
        return@toTorrent "下载种子失败, ${it.message}".toPlainText()
    }.mapCatching { file ->
        subject.uploadFile("${title.toFullWidth()}.torrent", file)
    }.onFailure {
        return@toTorrent "上传种子失败, ${it.message}".toPlainText()
    }.getOrNull()
}

internal fun Element.src() = attr("src")

internal fun Element.href() = attr("href")

internal fun Element.image(subject: Contact?): MessageContent = runBlocking {
    if (subject == null) return@runBlocking " [${src()}] ".toPlainText()

    runCatching {
        val url = Url(src())
        ImageFolder.resolve(url.filename).apply {
            if (exists().not()) {
                parentFile.mkdirs()
                writeBytes(client.useHttpClient { it.get(url) })
            }
        }
    }.mapCatching { image ->
        image.uploadAsImage(subject)
    }.getOrElse {
        " [${src()}] ".toPlainText()
    }
}

fun Element.toMessage(subject: Contact?): MessageChain = buildMessageChain {
    NodeTraversor.traverse(object : NodeVisitor {
        override fun head(node: Node, depth: Int) {
            if (node is TextNode) {
                append(node.wholeText)
            }
        }

        override fun tail(node: Node, depth: Int) {
            if (node is Element) {
                when (node.nodeName()) {
                    "img" -> {
                        append(node.image(subject))
                    }
                    "a" -> {
                        if (node.text() != node.href()) {
                            append(" <${node.href()}> ")
                        }
                    }
                    else -> {
                        //
                    }
                }
            }
        }
    }, this@toMessage)
}