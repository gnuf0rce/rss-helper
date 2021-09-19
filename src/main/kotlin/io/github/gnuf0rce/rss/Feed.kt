package io.github.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.*
import java.time.*
import java.util.*
import javax.net.ssl.*
import kotlin.properties.*

internal suspend fun RssHttpClient.feed(url: Url): SyndFeed = useHttpClient { client ->
    runCatching{
        client.get<SyndFeed>(url) {
            header(HttpHeaders.Host, url.host)
        }
    }.recoverCatching {
        when(it) {
            is SSLException -> {
                throw SSLException("Host: ${url.host}, ${it.message}", it)
            }
            is ResponseException -> {
                throw if ("Cloudflare" in it.message.orEmpty()) CloudflareException(it) else it
            }
            else -> throw it
        }
    }.getOrThrow()
}

class CloudflareException(override val cause: ResponseException): IllegalStateException("Need Cloudflare CAPTCHA", cause)

private val SystemZoneOffset by lazy { OffsetDateTime.now().offset!! }

private fun Date.toOffsetDateTime() = toInstant().atOffset(SystemZoneOffset)

internal fun timestamp(second: Long) = Instant.ofEpochSecond(second).atOffset(SystemZoneOffset)

internal fun OffsetDateTime?.orMinimum(): OffsetDateTime = this ?: OffsetDateTime.MIN

internal fun OffsetDateTime?.orNow(): OffsetDateTime = this ?: OffsetDateTime.now()

internal val SyndEntry.published get() = publishedDate?.toOffsetDateTime()

internal val SyndEntry.updated get() = updatedDate?.toOffsetDateTime()

internal val SyndEntry.last get() = updated ?: published

internal val SyndFeed.published get() = publishedDate?.toOffsetDateTime() ?: entries.maxOfOrNull { it.last.orMinimum() }

private fun ContentType(value: String): ContentType {
    return if ('/' in value) {
        ContentType.parse(value)
    } else {
        ContentType.parse("text/${value}")
    }
}

internal val SyndContent.contentType get() = ContentType(type)

internal val SyndEnclosure.contentType get() = ContentType(type)

internal val Bittorrent = ContentType.parse("application/x-bittorrent")

internal val Html by ContentType.Text::Html

internal val Plain by ContentType.Text::Plain

internal val SyndContent.text get() = if (Html.match(contentType)) Jsoup.parse(value).text() else value

internal fun SyndEntry.find(type: ContentType) = (contents + description).find { type.match(it.contentType) }

/**
 * 查找第一个 [ContentType] 为 [Html] 的 [SyndContent]，转换为 [org.jsoup.nodes.Document]
 */
internal val SyndEntry.html get() = find(Html)?.let { Jsoup.parse(it.value) }

/**
 * 查找第一个 [ContentType] 为 [Plain] 的 [SyndContent]
 */
internal val SyndEntry.text get() = find(Plain)?.value

/**
 * 查找第一个 [ContentType] 为 [Bittorrent] 的 [SyndEnclosure]，取 URL
 */
internal val SyndEntry.torrent by ReadOnlyProperty { entry, _ ->
    val url = entry.enclosures.orEmpty().find { Bittorrent.match(it.contentType) }?.url?.let {
        if (it.startsWith("magnet")) it.substringBefore("&") else it
    }
    url ?: entry.link?.takeIf { it.endsWith(".torrent") }
}