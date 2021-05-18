package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.RssHelperPlugin
import io.gnuf0rce.mirai.plugin.RssSubscriber
import io.gnuf0rce.mirai.plugin.toMessage
import io.gnuf0rce.rss.feed
import io.ktor.http.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.toPlainText
import okio.ByteString.Companion.encode
import kotlin.time.minutes

object RssBaseCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss",
    description = "Rss 订阅、取消、详情等相关指令",
    overrideContext = RssCommandArgumentContext
) {

    @SubCommand
    @Description("添加一个订阅")
    suspend fun CommandSenderOnMessage<*>.add(url: Url) = sendMessage {
        RssSubscriber.add(url, fromEvent.subject).let { (name, _, _) ->
            "RSS订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("列出订阅列表")
    suspend fun CommandSenderOnMessage<*>.list() = sendMessage {
        RssSubscriber.list(fromEvent.subject).entries.joinToString("\n") { (url, record) ->
            val base64 = url.toString().encode().base64()
            "[${record.name}](${base64}) <${record.interval.minutes}>"
        }.toPlainText()
    }

    @SubCommand
    @Description("添加一个订阅")
    suspend fun CommandSenderOnMessage<*>.interval(url: Url, duration: Int) = sendMessage {
        RssSubscriber.interval(url, duration).let { (name, _, _) ->
            "RSS订阅任务[${name}]设置订阅时间${duration}".toPlainText()
        }
    }

    @SubCommand
    @Description("取消一个订阅")
    suspend fun CommandSenderOnMessage<*>.stop(url: Url) = sendMessage {
        RssSubscriber.stop(url, fromEvent.subject).let { (name, _, _) ->
            "RSS订阅任务[${name}]已取消".toPlainText()
        }
    }

    @SubCommand
    @Description("测试一个订阅")
    suspend fun CommandSenderOnMessage<*>.text(url: Url) = sendMessage {
        feed(url).entries.first().toMessage(fromEvent.subject)
    }
}