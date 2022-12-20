package io.github.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

@PublishedApi
internal class RomePlugin(val accept: List<ContentType>, val parser: () -> SyndFeedInput) {

    data class Config(
        var accept: MutableList<ContentType> = mutableListOf(
            ContentType.Text.Xml,
            ContentType.Application.Xml,
            ContentType.Application.Atom,
            ContentType.Application.Rss,
        ),
        var parser: () -> SyndFeedInput = { SyndFeedInput() }
    )

    constructor(config: Config) : this(config.accept, config.parser)

    companion object Plugin : HttpClientPlugin<Config, RomePlugin> {
        override val key: AttributeKey<RomePlugin> = AttributeKey("Rome")

        override fun prepare(block: Config.() -> Unit): RomePlugin = RomePlugin(Config().apply(block))

        override fun install(plugin: RomePlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) {
                for (type in plugin.accept) context.accept(type)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                if (!info.type.java.isAssignableFrom(SyndFeed::class.java)) return@intercept

                val type = context.response.contentType() ?: ContentType.Any
                val charset = context.response.charset() ?: Charsets.UTF_8
                if (plugin.accept.none { type.match(it) }) return@intercept

                val parsed = body.toInputStream()
                    .reader(charset)
                    .use { plugin.parser().build(it) }
                proceedWith(HttpResponseContainer(info, parsed))
            }
        }
    }
}