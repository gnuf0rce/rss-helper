package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource

object RssMoeCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-moe",
    description = "bangumi.moe Rss 订阅 系列 指令",
    overrideContext = RssCommandArgumentContext
) {
    private val moe = { ids: List<String> -> Url("https://bangumi.moe/rss/tags/${ids.joinToString("+")}") }

    private val ID_REGEX = """[a-z0-9]{24}""".toRegex()

    private suspend fun search(name: String): BangumiTag = client.useHttpClient { http ->
        http.post("https://bangumi.moe/api/tag/search") {
            setBody(body = buildJsonObject {
                put("keywords", true)
                put("multi", true)
                put("name", name)
            })
            contentType(ContentType.Application.Json)
        }.body()
    }

    private suspend fun recent(): List<BangumiRecent> = client.useHttpClient { http ->
        http.get("https://bangumi.moe/api/bangumi/recent").body()
    }

    private suspend fun BangumiRecent.cover(contact: Contact): Message {
        return try {
            client.useHttpClient { http ->
                http.get("https://bangumi.moe/$cover").body<ByteArray>()
            }.toExternalResource().use { resource ->
                contact.uploadImage(resource)
            }
        } catch (cause: Throwable) {
            "封面下载失败 $cause".toPlainText()
        }
    }

    @SubCommand
    @Description("查看当季番剧TAG")
    suspend fun CommandSenderOnMessage<*>.recent() = sendMessage {
        buildMessageChain {
            for (item in this@RssMoeCommand.recent()) {
                appendLine("<=====================>")
                add(item.cover(contact = fromEvent.subject))
                appendLine("番名: ${item.name}")
                appendLine("ID: ${item.tagId}")
            }
        }
    }

    @SubCommand
    @Description("搜索TAG")
    suspend fun CommandSenderOnMessage<*>.search(name: String) = sendMessage {
        buildMessageChain {
            val list = this@RssMoeCommand.search(name).tag
            if (list.isEmpty()) {
                appendLine("搜索结果为空")
                return@buildMessageChain
            }
            for (item in list) {
                appendLine("名称: ${item.locale["zh_cn"] ?: item.name}")
                appendLine("类型: ${item.type}")
                appendLine("ID: ${item.id}")
            }
        }
    }

    @SubCommand
    @Description("添加一个Tags订阅")
    suspend fun CommandSenderOnMessage<*>.tags(vararg ids: String) = sendMessage {
        check(ids.isNotEmpty()) { "ids 为空" }
        for (id in ids) check(id matches ID_REGEX) { "$id Not Matches ${ID_REGEX.pattern}" }
        val (name) = RssSubscriber.add(moe(ids.asList()), fromEvent.subject)
        "Moe订阅任务[${name}]已添加".toPlainText()
    }
}