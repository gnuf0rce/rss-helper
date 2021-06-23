package io.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.Jsoup
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.properties.ReadOnlyProperty

internal suspend fun feed(url: Url): SyndFeed = useHttpClient { it.get(url) }

private val SystemZoneOffset by lazy { OffsetDateTime.now().offset!! }

private fun Date.toOffsetDateTime() = toInstant().atOffset(SystemZoneOffset)

internal fun timestamp(second: Long) = Instant.ofEpochSecond(second).atOffset(SystemZoneOffset)

internal fun OffsetDateTime?.orMinimum(): OffsetDateTime = this ?: OffsetDateTime.MIN

internal fun OffsetDateTime?.orNow(): OffsetDateTime  = this ?: OffsetDateTime.now()

internal val SyndEntry.published get() = publishedDate?.toOffsetDateTime()

internal val SyndEntry.updated get() = updatedDate?.toOffsetDateTime()

internal val SyndEntry.last get() = updated ?: published

internal val SyndFeed.published get() = publishedDate?.toOffsetDateTime() ?: entries.maxOfOrNull { it.last.orMinimum() }

private fun parse(value: String): ContentType {
    return runCatching { ContentType.parse(value) }.getOrElse { ContentType.parse("text/${value}") }
}

internal val SyndContent.contentType get() = parse(type)

internal val SyndEnclosure.contentType get() = parse(type)

val Bittorrent = ContentType.parse("application/x-bittorrent")

val Html by ContentType.Text::Html

val Plain by ContentType.Text::Plain

internal val SyndContent.text get() = if (Html.match(contentType)) Jsoup.parse(value).text() else value

private fun SyndEntry.find(type: ContentType) = (contents + description).find { type.match(it.contentType) }

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
    entry.enclosures.orEmpty().find { Bittorrent.match(it.contentType) }?.url
        ?: entry.link?.takeIf { it.endsWith(".torrent") }
}