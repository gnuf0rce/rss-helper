package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*

@PublishedApi
internal object RssMiraiCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-mirai", "mirai",
    description = "Mirai论坛订阅、相关指令",
    overrideContext = RssCommandArgumentContext
) {
    private val Category = { category: Int -> Url("https://mirai.mamoe.net/category/$category.rss") }

    @SubCommand
    @Description("添加标签订阅")
    suspend fun CommandSenderOnMessage<*>.category(value: Int) = quote {
        val (name) = RssSubscriber.add(Category(value), fromEvent.subject)
        "MiraiForum订阅任务[${name}]已添加".toPlainText()
    }

    @SubCommand
    @Description("添加插件发布订阅")
    suspend fun CommandSenderOnMessage<*>.plugin() = category(11)

    @SubCommand
    @Description("添加其他项目发布订阅")
    suspend fun CommandSenderOnMessage<*>.other() = category(15)
}