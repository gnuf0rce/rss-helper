package io.github.gnuf0rce.mirai.plugin.command

import io.github.gnuf0rce.mirai.plugin.*
import io.github.gnuf0rce.rss.feed
import io.ktor.client.request.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import org.jsoup.*

object RssTestCommand: CompositeCommand(
    owner = RssHelperPlugin,
    "rss-test", "rss-tools",
    description = "Rss 测试 相关指令",
    overrideContext = RssCommandArgumentContext
) {
    @SubCommand
    @Description("测试一个订阅")
    suspend fun CommandSenderOnMessage<*>.build(url: Url) = sendMessage {
        client.feed(url).entries.first().toMessage(fromEvent.subject)
    }

    @SubCommand
    suspend fun CommandSender.ssl() {
        val html = Jsoup.parse(client.useHttpClient { it.get("https://ssl.haka.se/") })
        sendMessage(html.wholeText())
    }

    @SubCommand
    @Description("清空种子文件")
    suspend fun CommandSenderOnMessage<*>.clear(group: Group = fromEvent.subject as Group) = sendMessage {
        group.filesRoot.listFilesCollection().forEach { file ->
            if (file.getInfo()?.uploaderId  == group.bot.id && file.name.endsWith(".torrent")) {
                file.delete()
            }
        }
        "${group}清空种子文件完毕".toPlainText()
    }
}