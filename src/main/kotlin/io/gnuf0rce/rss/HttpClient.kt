package io.gnuf0rce.rss

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.internal.http2.ConnectionShutdownException
import okhttp3.internal.http2.StreamResetException
import java.io.EOFException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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
            scope.responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
                if (body !is ByteReadChannel)
                    return@intercept

                if (!info.type.java.isAssignableFrom(SyndFeed::class.java))
                    return@intercept

                if (feature.accept.isNotEmpty() && feature.accept.all {
                        context.response.contentType()?.match(it) != true
                    })
                    return@intercept

                val parsedBody =
                    feature.parser().build(body.toInputStream().reader(context.response.charset() ?: Charsets.UTF_8))
                proceedWith(HttpResponseContainer(info, parsedBody))
            }
        }
    }
}

fun HttpClientConfig<*>.Rome(block: RomeFeature.Config.() -> Unit = {}) {
    install(RomeFeature, block)
}

typealias Ignore = (Throwable) -> Boolean

private val ignore: Ignore = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is SocketException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is NullPointerException,
        is UnknownHostException,
        is ConnectionShutdownException,
        -> true
        else -> false
    }
}

private val DefaultSyndFeedInput: SyndFeedInput = SyndFeedInput()

// TODO 代理, Doh
private fun client() = HttpClient(OkHttp) {
    BrowserUserAgent()
    ContentEncoding {
        gzip()
        deflate()
        identity()
    }
    Rome {
        accept = mutableListOf()
        parser = { DefaultSyndFeedInput }
    }
}

internal suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = withContext(Dispatchers.IO) {
    while (isActive) {
        runCatching {
            client().use { block(it) }
        }.onSuccess {
            return@withContext it
        }.onFailure { throwable ->
            if (isActive && ignore(throwable)) {
                //
            } else {
                throw throwable
            }
        }
    }
    throw CancellationException()
}