package io.github.gnuf0rce.rss

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

class RomeFeature internal constructor(val accept: List<ContentType>, val parser: () -> SyndFeedInput) {

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

    companion object Feature : HttpClientFeature<Config, RomeFeature> {
        override val key: AttributeKey<RomeFeature> = AttributeKey("Rome")

        override fun prepare(block: Config.() -> Unit): RomeFeature = RomeFeature(Config().apply(block))

        override fun install(feature: RomeFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) {
                for (type in feature.accept) context.accept(type)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                if (!info.type.java.isAssignableFrom(SyndFeed::class.java)) return@intercept

                if (!feature.accept.any { context.response.contentType()?.match(it) == true }) return@intercept

                val reader = body.toInputStream().reader(context.response.charset() ?: Charsets.UTF_8)
                val parsed = feature.parser().build(reader)
                proceedWith(HttpResponseContainer(info, parsed))
            }
        }
    }
}