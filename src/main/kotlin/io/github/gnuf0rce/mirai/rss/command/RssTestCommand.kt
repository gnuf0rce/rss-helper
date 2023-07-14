package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.github.gnuf0rce.mirai.rss.data.*
import io.github.gnuf0rce.rss.feed
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.util.ContactUtils.render
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import org.jsoup.*

@PublishedApi
internal object RssTestCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-test", "rss-tools",
    description = "Rss 测试 相关指令",
    overrideContext = RssCommandArgumentContext
) {
    @SubCommand
    @Description("测试一个订阅")
    suspend fun CommandSender.build(url: Url, forward: Boolean = RssContentConfig.forward) {
        val feed = client.feed(url)
        if (feed.entries.isNullOrEmpty()) {
            sendMessage("结果为空".toPlainText())
        } else {
            if (subject != null) {
                feed.entries.first().toMessage(subject!!, Int.MAX_VALUE, forward)
            } else {
                sendMessage(feed.entries.first().title)
            }
        }
    }

    @SubCommand
    suspend fun CommandSender.ssl() {
        val html = client.useHttpClient { http ->
            http.get("https://ssl.haka.se/").body<String>()
        }
        val document = Jsoup.parse(html)
        sendMessage(document.wholeText())
    }

    @SubCommand
    @Description("清空种子文件")
    suspend fun CommandSenderOnMessage<*>.clear(group: Group = fromEvent.subject as Group) = quote {
        group.files.root.createFolder("torrent").files().collect { file -> file.delete() }
        "${group.render()}清空种子文件完毕".toPlainText()
    }
}