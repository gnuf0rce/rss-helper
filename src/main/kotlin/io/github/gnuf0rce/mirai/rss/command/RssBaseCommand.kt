package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.ktor.http.*
import io.ktor.util.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*

object RssBaseCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss",
    description = "Rss 订阅、取消、详情等相关指令",
    overrideContext = RssCommandArgumentContext
) {

    @SubCommand
    @Description("添加一个订阅")
    suspend fun CommandSenderOnMessage<*>.add(url: Url) = sendMessage {
        val (name) = RssSubscriber.add(url, fromEvent.subject)
        "RSS订阅任务[${name}]已添加".toPlainText()
    }

    @SubCommand
    @Description("列出订阅列表")
    suspend fun CommandSenderOnMessage<*>.list() = sendMessage {
        RssSubscriber.list(fromEvent.subject).entries.joinToString("\n") { (url, record) ->
            val base64 = url.toString().encodeBase64()
            "[${record.name}](${base64})<${record.interval}>"
        }.ifBlank { "列表为空" }.toPlainText()
    }

    @SubCommand
    @Description("设置订阅间隔")
    suspend fun CommandSenderOnMessage<*>.interval(url: Url, duration: Int) = sendMessage {
        val (name) = RssSubscriber.interval(url, duration)
        "RSS订阅任务[${name}]设置订阅间隔${duration}m".toPlainText()
    }

    @SubCommand
    @Description("取消一个订阅")
    suspend fun CommandSenderOnMessage<*>.stop(url: Url) = sendMessage {
        val (name) = RssSubscriber.stop(url, fromEvent.subject)
        "RSS订阅任务[${name}]已取消".toPlainText()
    }
}