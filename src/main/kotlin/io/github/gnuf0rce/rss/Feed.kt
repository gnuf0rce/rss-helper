package io.github.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.parser.*
import java.time.*
import java.util.*
import javax.net.ssl.*
import kotlin.properties.*

internal suspend fun RssHttpClient.feed(url: Url): SyndFeed = useHttpClient { http ->
    try {
        http.get(url) {
            header(HttpHeaders.Host, url.host)
        }.body()
    } catch (cause: Exception) {
        throw when (cause) {
            is SSLException -> {
                SSLException("Host: ${url.host}, ${cause.message}", cause)
            }
            is ResponseException -> {
                if ("Cloudflare" in cause.message.orEmpty()) CloudflareException(cause) else cause
            }
            else -> cause
        }
    }
}

class CloudflareException(override val cause: ResponseException) : IllegalStateException("Need Cloudflare CAPTCHA")

private fun Date.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

internal fun timestamp(mills: Long): OffsetDateTime {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(mills), ZoneId.systemDefault())
}

internal fun OffsetDateTime?.orMin(): OffsetDateTime = this ?: OffsetDateTime.MIN

internal fun OffsetDateTime?.orNow(): OffsetDateTime = this ?: OffsetDateTime.now()

internal val SyndEntry.published get() = publishedDate?.toOffsetDateTime()

internal val SyndEntry.updated get() = updatedDate?.toOffsetDateTime()

internal val SyndEntry.last get() = updated ?: published

private fun ContentType(value: String?): ContentType {
    if (value == null) return ContentType.Text.Plain
    return if ('/' in value) {
        ContentType.parse(value)
    } else {
        ContentType("text", value)
    }
}

internal val SyndContent.contentType get() = ContentType(type)

internal val SyndEnclosure.contentType get() = ContentType(type)

internal val Bittorrent = ContentType.parse("application/x-bittorrent")

internal fun SyndEntry.find(type: ContentType) = (contents + description).find { type.match(it.contentType) }

/**
 * 查找第一个 [ContentType] 为 [ContentType.Text.Html] 的 [SyndContent]，转换为 [org.jsoup.nodes.Document]
 * @see Parser.parseBodyFragment
 */
internal val SyndEntry.html get() = find(ContentType.Text.Html)?.let { Parser.parseBodyFragment(it.value, uri) }

/**
 * 查找第一个 [ContentType] 为 [ContentType.Text.Plain] 的 [SyndContent]
 * @see Parser.unescapeEntities
 */
internal val SyndEntry.text get() = find(ContentType.Text.Plain)?.let { Parser.unescapeEntities(it.value, false) }

/**
 * 查找第一个 [ContentType] 为 [Bittorrent] 的 [SyndEnclosure]，取 URL
 * @see Bittorrent
 */
internal val SyndEntry.torrent by ReadOnlyProperty { entry, _ ->
    val url = entry.enclosures
        ?.find { Bittorrent.match(it.contentType) }
        ?.url?.let { if (it.startsWith("magnet")) it.substringBefore("&") else it }

    url ?: entry.link?.takeIf { it.endsWith(".torrent") }
}