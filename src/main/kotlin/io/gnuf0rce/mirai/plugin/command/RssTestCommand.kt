package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.RssHelperPlugin
import io.gnuf0rce.mirai.plugin.toMessage
import io.gnuf0rce.rss.feed
import io.ktor.http.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand

object RssTestCommand: CompositeCommand(
    owner = RssHelperPlugin,
    "rss-test",
    description = "Rss 测试 相关指令",
    overrideContext = RssCommandArgumentContext
) {
    @SubCommand
    @Description("测试一个订阅")
    suspend fun CommandSenderOnMessage<*>.build(url: Url) = sendMessage {
        feed(url).entries.first().toMessage(fromEvent.subject)
    }
}