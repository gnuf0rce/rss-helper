package io.github.gnuf0rce.mirai.rss.command

import io.ktor.http.*
import io.ktor.util.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote

@PublishedApi
internal val RssCommandArgumentContext: CommandArgumentContext = buildCommandArgumentContext {
    Url::class with { raw, _ ->
        try {
            if (raw.startsWith("http")) {
                Url(raw)
            } else {
                Url(raw.decodeBase64String())
            }
        } catch (cause: Exception) {
            throw CommandArgumentParserException("无法解析${raw}为URL", cause)
        }
    }
}

@PublishedApi
internal suspend fun <T : CommandSenderOnMessage<*>> T.quote(block: suspend T.(Contact) -> Message) {
    val message = block(fromEvent.subject)
    sendMessage(fromEvent.message.quote() + message)
}