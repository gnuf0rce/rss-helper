package io.github.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.nodes.*
import org.jsoup.parser.*
import java.net.*
import java.time.*
import java.util.*
import javax.net.ssl.*
import kotlin.properties.*

@PublishedApi
internal suspend fun RssHttpClient.feed(url: Url): SyndFeed = useHttpClient { http ->
    try {
        http.get(url) {
            header(HttpHeaders.Host, url.host)
        }.body()
    } catch (cause: Exception) {
        throw when (cause) {
            is SSLException -> {
                HostConnectException(url.host, cause)
            }
            is SocketException -> {
                if ("Connection" in cause.message.orEmpty()) HostConnectException(url.host, cause) else cause
            }
            is ResponseException -> {
                if ("Cloudflare" in cause.message.orEmpty()) CloudflareException(cause) else cause
            }
            else -> cause
        }
    }
}

private fun Date.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

@PublishedApi
internal fun timestamp(mills: Long): OffsetDateTime {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(mills), ZoneId.systemDefault())
}

@PublishedApi
internal fun OffsetDateTime?.orMin(): OffsetDateTime = this ?: OffsetDateTime.MIN

@PublishedApi
internal fun OffsetDateTime?.orNow(): OffsetDateTime = this ?: OffsetDateTime.now()

@PublishedApi
internal val SyndEntry.published: OffsetDateTime? get() = publishedDate?.toOffsetDateTime()

@PublishedApi
internal val SyndEntry.updated: OffsetDateTime? get() = updatedDate?.toOffsetDateTime()

@PublishedApi
internal val SyndEntry.last: OffsetDateTime? get() = (updatedDate ?: publishedDate)?.toOffsetDateTime()

private fun ContentType(value: String?): ContentType {
    if (value == null) return ContentType.Text.Plain
    return if ('/' in value) {
        ContentType.parse(value)
    } else {
        ContentType("text", value)
    }
}

@PublishedApi
internal val SyndContent.contentType: ContentType get() = ContentType(type)

@PublishedApi
internal val SyndEnclosure.contentType: ContentType get() = ContentType(type)

@PublishedApi
internal val Bittorrent: ContentType = ContentType.parse("application/x-bittorrent")

@PublishedApi
internal fun SyndEntry.find(type: ContentType): SyndContent? {
    for (content in contents) {
        if (type.match(content.contentType)) return content
    }
    if (type.match(description.contentType)) return description
    return null
}


/**
 * 查找第一个 [ContentType] 为 [ContentType.Text.Html] 的 [SyndContent]，转换为 [org.jsoup.nodes.Document]
 * @see Parser.parseBodyFragment
 */
@PublishedApi
internal val SyndEntry.html: Document?
    get() = find(ContentType.Text.Html)?.let {
        Parser.parseBodyFragment(
            it.value,
            uri
        )
    }

/**
 * 查找第一个 [ContentType] 为 [ContentType.Text.Plain] 的 [SyndContent]
 * @see Parser.unescapeEntities
 */
@PublishedApi
internal val SyndEntry.text: String?
    get() = find(ContentType.Text.Plain)?.let {
        Parser.unescapeEntities(
            it.value,
            false
        )
    }

/**
 * 查找第一个 [ContentType] 为 [Bittorrent] 的 [SyndEnclosure]，取 URL
 * @see Bittorrent
 */
@PublishedApi
internal val SyndEntry.torrent: String? by ReadOnlyProperty { entry, _ ->
    val url = entry.enclosures
        ?.find { Bittorrent.match(it.contentType) }
        ?.url?.let { if (it.startsWith("magnet")) it.substringBefore("&") else it }

    url ?: entry.link?.takeIf { it.endsWith(".torrent") }
}