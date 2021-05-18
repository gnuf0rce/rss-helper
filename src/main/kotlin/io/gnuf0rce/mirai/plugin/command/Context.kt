package io.gnuf0rce.mirai.plugin.command

import io.ktor.http.*
import kotlinx.coroutines.delay
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.descriptor.CommandArgumentParserException
import net.mamoe.mirai.console.command.descriptor.buildCommandArgumentContext
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.toPlainText
import okio.ByteString.Companion.decodeBase64
import kotlin.time.minutes

val RssCommandArgumentContext = buildCommandArgumentContext {
    Url::class with { raw, _ ->
        runCatching {
            Url(raw.decodeBase64()?.utf8() ?: raw)
        }.getOrElse {
            throw CommandArgumentParserException("无法解析${raw}为URL", it)
        }
    }
}

val SendLimit = """本群每分钟只能发\d+条消息""".toRegex()

suspend fun <T : CommandSenderOnMessage<*>> T.sendMessage(block: suspend T.(Contact) -> Message): Boolean {
    return runCatching {
        block(fromEvent.subject)
    }.onSuccess { message ->
        quoteReply(message)
    }.onFailure {
        when {
            SendLimit.containsMatchIn(it.message.orEmpty()) -> {
                delay((1).minutes)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                quoteReply("发送消息失败， ${it.message}")
            }
        }
    }.isSuccess
}

suspend fun CommandSenderOnMessage<*>.quoteReply(message: Message) = sendMessage(fromEvent.message.quote() + message)

suspend fun CommandSenderOnMessage<*>.quoteReply(message: String) = quoteReply(message.toPlainText())