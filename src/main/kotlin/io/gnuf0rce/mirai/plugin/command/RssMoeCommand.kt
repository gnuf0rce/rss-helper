package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import java.io.*

object RssMoeCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-moe",
    description = "bangumi.moe Rss 订阅 系列 指令",
    overrideContext = RssCommandArgumentContext
) {
    private val moe = { ids: List<String> -> Url("https://bangumi.moe/rss/tags/${ids.joinToString("+")}") }

    private val ID_REGEX = """[a-z0-9]{24}""".toRegex()

    private suspend fun search(name: String): BangumiTag = client.useHttpClient {
        it.post("https://bangumi.moe/api/tag/search") {
            body = buildJsonObject {
                put("keywords", true)
                put("multi", true)
                put("name", name)
            }
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun recent(): List<BangumiRecent> = client.useHttpClient {
        it.get("https://bangumi.moe/api/bangumi/recent")
    }

    private suspend fun BangumiRecent.cover(contact: Contact): Message {
        return runCatching {
            client.useHttpClient { it.get<InputStream>("https://bangumi.moe/$cover") }.uploadAsImage(contact)
        }.getOrElse {
            "封面下载失败 $it".toPlainText()
        }
    }

    @SubCommand
    @Description("查看当季番剧TAG")
    suspend fun CommandSenderOnMessage<*>.recent() = sendMessage {
        buildMessageChain {
            this@RssMoeCommand.recent().forEach {
                appendLine("<=====================>")
                add(it.cover(contact = fromEvent.subject))
                appendLine("番名: ${it.name}")
                appendLine("ID: ${it.tagId}")
            }
        }
    }

    @SubCommand
    @Description("搜索TAG")
    suspend fun CommandSenderOnMessage<*>.search(name: String) = sendMessage {
        buildMessageChain {
            this@RssMoeCommand.search(name).tag.apply {
                if (isEmpty()) {
                    appendLine("搜索结果为空")
                    return@buildMessageChain
                }
            }.forEach {
                appendLine("名称: ${it.locale["zh_cn"] ?: it.name}")
                appendLine("类型: ${it.type}")
                appendLine("ID: ${it.id}")
            }
        }
    }

    @SubCommand
    @Description("添加一个Tags订阅")
    suspend fun CommandSenderOnMessage<*>.tags(vararg ids: String) = sendMessage {
        check(ids.isNotEmpty()) { "ids 为空" }
        ids.forEach { check(it.matches(ID_REGEX)) { "$it Not Matches ${ID_REGEX.pattern}" } }
        RssSubscriber.add(moe(ids.asList()), fromEvent.subject).let { (name, _, _) ->
            "Moe订阅任务[${name}]已添加".toPlainText()
        }
    }
}