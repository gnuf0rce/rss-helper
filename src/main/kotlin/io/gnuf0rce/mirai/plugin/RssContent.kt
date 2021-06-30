package io.gnuf0rce.mirai.plugin

import com.rometools.rome.feed.synd.SyndEntry
import io.gnuf0rce.rss.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.FileSupported
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.RemoteFile.Companion.uploadFile
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

internal val logger by RssHelperPlugin::logger

internal val ImageFolder get() = RssHelperPlugin.dataFolder.resolve("image")

internal val TorrentFolder get() = RssHelperPlugin.dataFolder.resolve("torrent")

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

fun SyndEntry.toMessage(subject: Contact? = null, content: Boolean = true) = buildMessageChain {
    appendKeyValue("标题", title)
    appendKeyValue("链接", link)
    appendKeyValue("发布时间", published)
    appendKeyValue("更新时间", updated)
    appendKeyValue("分类", categories.map { it.name })
    appendKeyValue("作者", author)
    appendKeyValue("种子", torrent)
    if (content) {
        add(html?.toMessage(subject) ?: text.orEmpty().toPlainText())
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
    val url = Url(torrent ?: return null)
    return runCatching {
        TorrentFolder.resolve("${uri.toFullWidth()}.torrent").apply {
            if (exists().not()) {
                parentFile.mkdirs()
                writeBytes(useHttpClient { it.get(url) })
            }
        }
    }.onFailure {
        return@toTorrent "下载种子失败, ${it.message}".toPlainText()
    }.mapCatching { file ->
        subject.uploadFile("${uri.toFullWidth()}.torrent", file)
    }.onFailure {
        return@toTorrent "上传种子失败, ${it.message}".toPlainText()
    }.getOrNull()
}

internal fun Element.src() = attr("src")

internal fun Element.href() = attr("href")

internal fun Element.image(subject: Contact?): MessageContent = runBlocking {
    if (subject == null) {
        " [${src()}] ".toPlainText()
    } else {
        val url = Url(src())
        runCatching {
            ImageFolder.resolve(url.filename).apply {
                if (exists().not()) {
                    parentFile.mkdirs()
                    writeBytes(useHttpClient { it.get(url) })
                }
            }
        }.mapCatching { image ->
            image.uploadAsImage(subject)
        }.getOrElse {
            " [${src()}] ".toPlainText()
        }
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
                when(node.nodeName()) {
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