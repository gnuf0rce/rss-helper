package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.RssHelperPlugin
import io.gnuf0rce.mirai.plugin.RssSubscriber
import io.ktor.http.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.toPlainText

object RssMiraiCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-mirai", "mirai",
    description = "Mirai论坛订阅、相关指令",
    overrideContext = RssCommandArgumentContext
) {
    private val Category = { category: Int -> Url("https://mirai.mamoe.net/category/$category.rss") }

    @SubCommand
    @Description("添加插件发布订阅")
    suspend fun CommandSenderOnMessage<*>.category(value: Int) = sendMessage {
        RssSubscriber.add(Category(value), fromEvent.subject).let { (name, _, _) ->
            "MiraiForum订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("添加插件发布订阅")
    suspend fun CommandSenderOnMessage<*>.plugin() = category(11)

    @SubCommand
    @Description("添加其他项目发布发布订阅")
    suspend fun CommandSenderOnMessage<*>.other() = category(15)
}