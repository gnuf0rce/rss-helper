package io.gnuf0rce.mirai.plugin

import com.rometools.rome.feed.synd.SyndEntry
import io.gnuf0rce.rss.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.FileSupported
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.RemoteFile.Companion.uploadFile
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

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

suspend fun SyndEntry.toMessage(subject: Contact, content: Boolean = true) = buildMessageChain {
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

suspend fun SyndEntry.toTorrent(subject: FileSupported): Message? {
    val url = Url(torrent ?: return null)
    return runCatching {
        TorrentFolder.resolve("${uri}.torrent").apply {
            if (exists().not()) {
                parentFile.mkdirs()
                writeBytes(useHttpClient { it.get(url) })
            }
        }
    }.onFailure {
        return@toTorrent "下载种子失败, ${it.message}".toPlainText()
    }.mapCatching { file ->
        subject.uploadFile("${uri}.torrent", file)
    }.onFailure {
        return@toTorrent "上传种子失败, ${it.message}".toPlainText()
    }.getOrNull()
}

suspend fun Element.toMessage(subject: Contact?): MessageChain = buildMessageChain {
    if (this@toMessage is Document) {
        append(body().toMessage(subject))
        return@buildMessageChain
    }
    when (nodeName()) {
        "br" -> {
            appendLine("")
        }
        "img" -> {
            if (subject != null) {
                val url = Url(attr("src"))
                runCatching {
                    ImageFolder.resolve(url.filename).apply {
                        if (exists().not()) {
                            parentFile.mkdirs()
                            writeBytes(useHttpClient { it.get(url) })
                        }
                    }
                }.onFailure {
                    appendLine("[下载图片失败](${text()})")
                }.mapCatching { image ->
                    append(image.uploadAsImage(subject))
                }.onFailure {
                    appendLine("[上传图片失败](${text()})")
                }
            } else {
                appendLine(attr("src"))
            }
        }
        "a" -> {
            append(attr("href").toPlainText())
        }
        "hr" -> {
            appendLine("----------------")
        }
        "strong", "span", "h1", "h2", "h3", "h4", "h5", "b", "font" -> {
            append(text().trim().toPlainText())
        }
        "code", "table", "ul" -> {
            appendLine(text())
        }
        "body", "div", "p", "pre" -> {
            childNodes().forEach { node ->
                if (node is TextNode) {
                    append(node.text().trim())
                } else if (node is Element) {
                    append(node.toMessage(subject))
                }
            }
            appendLine("")
        }
        else -> {
            appendLine(html())
        }
    }
}