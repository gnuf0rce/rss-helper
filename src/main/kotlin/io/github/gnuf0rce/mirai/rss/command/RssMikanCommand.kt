package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*

@PublishedApi
internal object RssMikanCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-mikan", "mikan",
    description = "Mikan Plans Rss 订阅 系列 指令",
    overrideContext = RssCommandArgumentContext
) {

    private val MyBangumi = { token: String -> Url("https://mikanani.me/RSS/MyBangumi?token=$token") }

    private val Classic = Url("https://mikanani.me/RSS/Classic")

    private val Bangumi = { id: Int, sub: Int? -> Url("https://mikanani.me/RSS/Bangumi?bangumiId=$id&subgroupid=$sub") }

    private val Search = { word: String -> Url("https://mikanani.me/Home/Search?searchstr=$word") }

    @SubCommand
    @Description("添加一个MyBangumi订阅")
    suspend fun CommandSenderOnMessage<*>.my(token: String) = quote {
        val (name) = RssSubscriber.add(MyBangumi(token), fromEvent.subject)
        "MyBangumi($token)订阅任务[${name}]已添加".toPlainText()
    }

    @SubCommand
    @Description("添加一个Classic订阅")
    suspend fun CommandSenderOnMessage<*>.classic() = quote {
        val (name) = RssSubscriber.add(Classic, fromEvent.subject)
        "Classic订阅任务[${name}]已添加".toPlainText()
    }

    @SubCommand
    @Description("添加一个Bangumi订阅")
    suspend fun CommandSenderOnMessage<*>.bangumi(id: Int, sub: Int? = null) = quote {
        val (name) = RssSubscriber.add(Bangumi(id, sub), fromEvent.subject)
        "Bangumi($id by $sub)订阅任务[${name}]已添加".toPlainText()
    }

    @SubCommand
    @Description("添加一个Search订阅")
    suspend fun CommandSenderOnMessage<*>.search(word: String) = quote {
        val (name) = RssSubscriber.add(Search(word), fromEvent.subject)
        "Search($word)订阅任务[${name}]已添加".toPlainText()
    }
}